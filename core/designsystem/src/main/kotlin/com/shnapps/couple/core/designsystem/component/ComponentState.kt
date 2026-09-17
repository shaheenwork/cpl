package com.shnapps.couple.core.designsystem.component

/**
 * UI state enums for the component inventory.
 *
 * Grouped here rather than beside each composable so every component file holds exactly
 * one public composable, and so the full set of states a component can be in is visible
 * in one place.
 *
 * None of these are domain types. Domain concepts — Intensity, BoundaryLevel,
 * PreferenceValue, Mood — live in :core:model and are imported, never redefined.
 */

/** Where a chapter sits in the night (BUILD_PROMPT.md §16). */
enum class ChapterState { Locked, Active, Complete }

/** Where a reveal currently is. Driven by the caller; the component owns no state. */
enum class RevealState { Concealed, Revealing, Revealed }

/** What the voice recorder is doing. The caller owns the audio; the component owns the look. */
enum class VoiceState { Idle, Recording, Recorded, Playing }

/** Visual weight of a call to action. */
enum class GlowButtonStyle { Primary, Secondary, Quiet }
