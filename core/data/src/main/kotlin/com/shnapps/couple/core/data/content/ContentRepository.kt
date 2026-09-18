package com.shnapps.couple.core.data.content

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.DispatcherProvider
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.common.outcomeOf
import com.shnapps.couple.core.data.taxonomy.TaxonomyParser
import com.shnapps.couple.core.database.content.ContentBundleEntity
import com.shnapps.couple.core.database.content.ContentDao
import com.shnapps.couple.core.database.content.ContentDeltaEntity
import com.shnapps.couple.core.database.content.ContentItemEntity
import com.shnapps.couple.core.firebase.content.ContentRemoteDataSource
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.ContentState
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Taxonomy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Content on the device (BUILD_PROMPT.md §9.2): the bundle shipped in the app or a newer
 * one downloaded since, cached in Room, with the admin deltas from Firestore applied on top.
 *
 * Everything here works offline. The network is only ever asked whether there is something
 * newer, never for the content itself while it is being used.
 *
 * This is the engine's input, not a browsing API: what a couple is shown is decided by the
 * engine behind the boundary filter (§3.2), and no product screen lists content directly.
 */
interface ContentRepository {
    /** Every item the engine may use: published, and not disabled by a delta. */
    val items: Flow<List<ContentItem>>

    val packs: Flow<List<ContentPack>>

    /** The taxonomy from the installed bundle (§9.5), so it updates with the content. */
    val taxonomy: Flow<Taxonomy>

    val state: Flow<ContentState>

    /** Installs the shipped bundle unless the device already has it or something newer. */
    suspend fun ensureInstalled(): Outcome<Unit>

    /**
     * Installs a newer bundle if the pointer has moved, and fetches new deltas. Each half is
     * attempted even if the other fails — a disable must land even when a download cannot.
     */
    suspend fun sync(): Outcome<ContentSyncResult>
}

data class ContentSyncResult(
    /** The version installed by this sync, or null if the device was already current. */
    val installedVersion: Int?,
    val deltasReceived: Int,
)

@Singleton
class DefaultContentRepository @Inject constructor(
    private val dao: ContentDao,
    private val shipped: ShippedContent,
    private val remote: ContentRemoteDataSource,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope appScope: CoroutineScope,
) : ContentRepository {

    private val installLock = Mutex()

    @Volatile
    private var shippedChecked = false

    override val items: Flow<List<ContentItem>> =
        combine(dao.observeItems(), dao.observeDeltas(), ::applyDeltas)
            .onStart { ensureInstalled() }
            .flowOn(dispatchers.default)
            .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val bundle: Flow<ContentBundleEntity> = dao.observeBundle()
        .onStart { ensureInstalled() }
        .filterNotNull()

    override val packs: Flow<List<ContentPack>> = bundle
        .map { it.packsJson }
        .distinctUntilChanged()
        .map(ContentBundleParser::parsePacks)
        .flowOn(dispatchers.default)

    override val taxonomy: Flow<Taxonomy> = bundle
        .map { it.taxonomyJson }
        .distinctUntilChanged()
        .map(TaxonomyParser::parse)
        .flowOn(dispatchers.default)
        .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override val state: Flow<ContentState> =
        combine(dao.observeBundle(), items, dao.observeDeltas()) { header, usable, deltas ->
            ContentState(
                contentVersion = header?.contentVersion ?: 0,
                itemCount = usable.size,
                deltaCount = deltas.size,
                lastSyncMillis = header?.lastSyncMillis,
            )
        }

    override suspend fun ensureInstalled(): Outcome<Unit> = withContext(dispatchers.io) {
        if (shippedChecked) return@withContext Outcome.Success(Unit)
        installLock.withLock {
            if (shippedChecked) return@withLock Outcome.Success(Unit)
            outcomeOf {
                val current = dao.bundle()
                if (shipped.version() > (current?.contentVersion ?: 0)) {
                    install(ContentBundleParser.parse(shipped.read()), current)
                }
                shippedChecked = true
            }
        }
    }

    override suspend fun sync(): Outcome<ContentSyncResult> = withContext(dispatchers.io) {
        val installed = ensureInstalled()
        if (installed is Outcome.Failure) return@withContext installed

        val bundleResult = syncBundle()
        val deltaResult = syncDeltas()
        when {
            bundleResult is Outcome.Failure -> bundleResult
            deltaResult is Outcome.Failure -> deltaResult
            else -> Outcome.Success(
                ContentSyncResult(
                    installedVersion = (bundleResult as Outcome.Success).value,
                    deltasReceived = (deltaResult as Outcome.Success).value,
                ),
            )
        }
    }

    /** The version installed, or null if already current. */
    private suspend fun syncBundle(): Outcome<Int?> {
        val latest = when (val pointer = remote.latestVersion()) {
            is Outcome.Failure -> return pointer
            is Outcome.Success -> pointer.value
        }
        val current = dao.bundle()
        if (current != null && latest <= current.contentVersion) return Outcome.Success(null)

        val text = when (val download = remote.downloadBundle(latest)) {
            is Outcome.Failure -> return download
            is Outcome.Success -> download.value
        }
        return installLock.withLock {
            val parsed = when (val read = outcomeOf { ContentBundleParser.parse(text) }) {
                is Outcome.Failure -> {
                    return@withLock Outcome.Failure(AppError.Validation(INVALID_BUNDLE, read.error.cause))
                }
                is Outcome.Success -> read.value
            }
            // A bundle must be the version it was fetched as. Anything else is a publishing
            // mistake, and installing it would leave the device's version number lying.
            if (parsed.contentVersion != latest) {
                return@withLock Outcome.Failure(AppError.Validation(VERSION_MISMATCH))
            }
            outcomeOf { install(parsed, dao.bundle()) }.map { latest }
        }
    }

    private suspend fun syncDeltas(): Outcome<Int> {
        val cursor = dao.bundle()?.deltaCursorMillis ?: 0L
        val deltas = when (val fetched = remote.deltasSince(cursor)) {
            is Outcome.Failure -> return fetched
            is Outcome.Success -> fetched.value
        }
        return outcomeOf {
            dao.upsertDeltas(
                deltas.map { ContentDeltaEntity(it.id, it.status, it.version, it.itemJson, it.updatedAtMillis) },
            )
            // Inclusive cursor: the newest delta is fetched once more next time, which costs one
            // read and means two deltas stamped in the same millisecond can never be skipped.
            val nextCursor = maxOf(cursor, deltas.maxOfOrNull { it.updatedAtMillis } ?: cursor)
            dao.markSynced(clock.nowMillis(), nextCursor)
            deltas.size
        }
    }

    private suspend fun install(parsed: ParsedBundle, previous: ContentBundleEntity?) {
        dao.installBundle(
            bundle = ContentBundleEntity(
                format = parsed.format,
                contentVersion = parsed.contentVersion,
                taxonomyVersion = parsed.taxonomyVersion,
                taxonomyJson = parsed.taxonomyJson,
                packsJson = parsed.packsJson,
                lastSyncMillis = previous?.lastSyncMillis,
                deltaCursorMillis = previous?.deltaCursorMillis ?: 0L,
            ),
            items = parsed.items.map { ContentItemEntity(it.id, it.version, it.pack, it.status, it.json) },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val INVALID_BUNDLE = "content_bundle_invalid"
        const val VERSION_MISMATCH = "content_bundle_version_mismatch"
    }
}

/**
 * The bundle with the deltas laid over it (§9.2, §9.6). A delta applies to its item at the
 * delta's version and earlier; a later bundle that raises the item's version past it wins.
 * An unknown delta status hides the item: an override this version cannot read must fail
 * closed, since it may be a disable.
 */
internal fun applyDeltas(rows: List<ContentItemEntity>, deltas: List<ContentDeltaEntity>): List<ContentItem> {
    val deltaById = deltas.associateBy { it.id }
    val bundled = rows.mapNotNull { row ->
        val delta = deltaById[row.id]?.takeIf { it.version >= row.version }
        // An edit this version cannot read hides the item rather than reviving the original
        // the admin replaced.
        val item = ContentBundleParser.parseItem(delta?.itemJson ?: row.json) ?: return@mapNotNull null
        val status = if (delta != null) ContentStatus.fromId(delta.status) else item.status
        item.takeIf { status == ContentStatus.PUBLISHED }?.copy(status = ContentStatus.PUBLISHED)
    }
    // Items an admin added before any bundle carried them.
    val bundledIds = rows.mapTo(HashSet()) { it.id }
    val added = deltas
        .filter { it.id !in bundledIds && ContentStatus.fromId(it.status) == ContentStatus.PUBLISHED }
        .mapNotNull { delta -> delta.itemJson?.let(ContentBundleParser::parseItem) }
        .map { it.copy(status = ContentStatus.PUBLISHED) }
    return bundled + added
}
