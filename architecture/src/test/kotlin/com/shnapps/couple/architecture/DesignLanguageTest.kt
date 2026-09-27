package com.shnapps.couple.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Test

/**
 * Rules that keep the visual language from coming apart (DESIGN_PROMPT.md §9).
 *
 * A visual decision with no gate is a visual decision that regresses two phases later.
 * These are the two worth failing a build over, because both are invisible in review: a
 * single emoji looks fine in a diff, and a hardcoded colour looks like a colour.
 */
class DesignLanguageTest {

    /**
     * No emoji in anything a person reads.
     *
     * The app used them as its icon set — 🔥 🎲 🌙 💌 👀 📖 ⏱ — and they were the single
     * clearest signal that nobody had designed it. They render differently on every
     * platform and every OS version, cannot be coloured or sized to a grid, cannot be
     * animated, and no two of them share an optical weight. `AfterhoursIcons` replaced
     * them (§4.9); this is what stops them coming back one button at a time.
     *
     * Comments are exempt, because nobody reads a KDoc on a phone, and several of them
     * quote the strings this rule exists to have removed.
     */
    @Test
    fun `no emoji reaches a screen`() {
        val offenders = uiFiles().flatMap { file ->
            file.text.lineSequence()
                .withIndex()
                .filter { (_, line) -> line.containsEmoji() && line.containsStringLiteral() }
                .filterNot { (_, line) -> line.isComment() }
                .map { (index, line) -> "${file.unixName}:${index + 1}  ${line.trim()}" }
        }
        assertNone(offenders) {
            "emoji in a user-facing string. Use AfterhoursIcons instead (DESIGN_PROMPT.md §4.9):\n" +
                offenders.joinToString("\n")
        }
    }

    /**
     * No literal colour outside the design system.
     *
     * The convention has been in CLAUDE.md since Phase 2 and held by review alone. One
     * `Color(0xFF...)` in a feature is how a palette stops being a palette, so it fails
     * the build now rather than a screenshot six phases later.
     */
    @Test
    fun `colour lives in the design system`() {
        val offenders = uiFiles()
            .filterNot { it.unixPath.contains("/core/designsystem/") }
            .flatMap { file ->
                file.text.lineSequence()
                    .withIndex()
                    .filter { (_, line) -> LITERAL_COLOR.containsMatchIn(line) }
                    .filterNot { (_, line) -> line.isComment() }
                    .map { (index, line) -> "${file.unixName}:${index + 1}  ${line.trim()}" }
            }
        assertNone(offenders) {
            "a literal colour outside :core:designsystem. Reach it through AfterhoursTheme:\n" +
                offenders.joinToString("\n")
        }
    }

    private companion object {
        /**
         * The emoji blocks that actually turn up in product copy: pictographs, dingbats,
         * symbols and the variation selector that makes ❤ into ❤️.
         */
        val EMOJI = Regex(
            "[🌀-🧿]|[☀-➿]|[⬀-⯿]|[⌀-⏿]|️",
        )

        /** `Color(0xFF...)` and `Color.Red`, which are the two ways this goes wrong. */
        val LITERAL_COLOR = Regex("""Color\(0x|Color\.(?!Transparent|Unspecified|Black|White)[A-Z]""")

        val UI_MODULES = listOf("/core/ui/", "/core/designsystem/", "/core/notifications/", "/feature/")
        val TEST_SOURCE_SETS = listOf("/src/test/", "/src/androidTest/", "/src/testDebug/")

        val KoFileDeclaration.unixPath: String get() = path.replace('\\', '/')
        val KoFileDeclaration.unixName: String get() = unixPath.substringAfterLast('/')

        fun uiFiles(): List<KoFileDeclaration> = Konsist.scopeFromProject().files
            .filterNot { file -> TEST_SOURCE_SETS.any { it in file.unixPath } }
            .filter { file -> UI_MODULES.any { it in file.unixPath } }

        fun String.containsEmoji(): Boolean = EMOJI.containsMatchIn(this)

        fun String.containsStringLiteral(): Boolean = contains('"')

        fun String.isComment(): Boolean = trimStart().let {
            it.startsWith("*") || it.startsWith("//") || it.startsWith("/*")
        }

        fun assertNone(offenders: List<String>, message: () -> String) {
            if (offenders.isNotEmpty()) throw AssertionError(message())
        }
    }
}
