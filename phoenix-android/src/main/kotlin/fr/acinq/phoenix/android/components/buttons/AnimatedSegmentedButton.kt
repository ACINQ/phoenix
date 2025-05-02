package fr.acinq.phoenix.android.components.buttons

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.utils.mutedTextColor

// Credits to Alex Lockwood: https://gist.github.com/alexjlockwood/9d23c23bb135738d9eb826b0298387c6

/**
 * A simple segmented control component. Pass two to four [SegmentedControlButton]s
 * as the segmented control's [content].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SegmentedControl(
    modifier: Modifier = Modifier,
    backgroundColor: Color = mutedTextColor.copy(alpha = .15f),
    backgroundShape: Shape = RoundedCornerShape(16.dp),
    buttonShape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.background(backgroundColor, shape = backgroundShape).clip(backgroundShape),
    ) {
        // Wrap the custom layout in a LookaheadScope, which is required in order
        // to make use of the Modifier.animateBounds() below.
        LookaheadScope {
            Layout(
                content = {
                    // Pass Modifier.animateBounds(this) so that the selected button background
                    // animates to its new position when its layout position changes.
                    SelectedBackground(Modifier.animateBounds(this), buttonShape)

                    // Pass the SegmentedControlButtons next.
                    content()
                },
                // Ensures the height of the segmented control is equal to the height
                // of the tallest button, and that it is read out as a selectable
                // group for accessibility.
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .selectableGroup(),
            ) { measurables, constraints ->
                require(measurables.count { it.layoutId == SelectedButtonId } <= 1) {
                    "Segmented control must have at most one selected button"
                }

                // Measure each button so that they have equal width.
                val buttonMeasurables = measurables.filter { it.layoutId != SelectedBackgroundId }
                val buttonWidth = constraints.maxWidth / buttonMeasurables.size
                val buttonConstraints = constraints.copy(minWidth = buttonWidth, maxWidth = buttonWidth)
                val buttonPlaceables = buttonMeasurables.map { it.measure(buttonConstraints) }

                // Measure the animated selected background if there is a selected button.
                val selectedButtonIndex = buttonMeasurables.indexOfFirst { it.layoutId == SelectedButtonId }
                val selectedBackgroundMeasurable = measurables.first { it.layoutId == SelectedBackgroundId }
                val selectedBackgroundPlaceable = if (selectedButtonIndex >= 0) {
                    selectedBackgroundMeasurable.measure(buttonConstraints)
                } else {
                    null
                }

                layout(
                    width = buttonPlaceables.sumOf { it.width },
                    height = buttonPlaceables.maxOf { it.height },
                ) {
                    // Place the selected background, if it exists.
                    selectedBackgroundPlaceable?.placeRelative(x = selectedButtonIndex * buttonWidth, y = 0)

                    // Place all the segmented control buttons.
                    buttonPlaceables.forEachIndexed { index, it ->
                        it.placeRelative(x = index * buttonWidth, y = 0)
                    }
                }
            }
        }
    }
}

/**
 * A button used as a child of [SegmentedControl].
 */
@Composable
fun SegmentedControlButton(
    onClick: () -> Unit,
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    buttonShape: Shape = RoundedCornerShape(16.dp),
) {
    Box(
        modifier = modifier
            .then(if (selected) Modifier.layoutId(SelectedButtonId) else Modifier)
            .clip(buttonShape)
            .selectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.padding(12.dp).alpha(if (selected) 1f else 0.3f),
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The animated button background that displays behind the currently selected button.
 */
@Composable
private fun SelectedBackground(modifier: Modifier = Modifier, buttonShape: Shape) {
    Surface(
        modifier = modifier.layoutId(SelectedBackgroundId),
        color = MaterialTheme.colors.surface,
        shape = buttonShape,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colors.primary),
    ) {}
}

private const val SelectedButtonId = "SelectedButtonId"
private const val SelectedBackgroundId = "SelectedBackgroundId"