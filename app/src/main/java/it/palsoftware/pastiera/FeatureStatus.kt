package it.palsoftware.pastiera

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

enum class FeatureStatus(
    val icon: ImageVector,
    @StringRes val contentDescriptionRes: Int,
    @StringRes val hintRes: Int
) {
    Construction(
        icon = FeatureStatusIcons.Construction,
        contentDescriptionRes = R.string.feature_status_construction_content_description,
        hintRes = R.string.feature_status_construction_hint
    ),
    Experimental(
        icon = FeatureStatusIcons.Experiment,
        contentDescriptionRes = R.string.feature_status_experimental_content_description,
        hintRes = R.string.feature_status_experimental_hint
    )
}

@Composable
fun FeatureStatusIcon(
    status: FeatureStatus,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = stringResource(status.contentDescriptionRes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = stringResource(status.hintRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

object FeatureStatusIcons {
    val Construction: ImageVector by lazy {
        ImageVector.Builder(
            name = "construction",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18.9f, 21f)
                lineTo(13.43f, 15.53f)
                lineToRelative(2.1f, -2.1f)
                lineTo(21f, 18.9f)
                lineTo(18.9f, 21f)
                close()
                moveTo(5.1f, 21f)
                lineTo(3f, 18.9f)
                lineTo(9.9f, 12f)
                lineTo(8.2f, 10.3f)
                lineTo(7.5f, 11f)
                lineTo(6.23f, 9.73f)
                verticalLineToRelative(2.05f)
                lineToRelative(-0.7f, 0.7f)
                lineTo(2.5f, 9.45f)
                lineTo(3.2f, 8.75f)
                horizontalLineTo(5.25f)
                lineTo(4f, 7.5f)
                lineTo(7.55f, 3.95f)
                quadTo(8.05f, 3.45f, 8.63f, 3.22f)
                reflectiveQuadTo(9.8f, 3f)
                reflectiveQuadToRelative(1.18f, 0.22f)
                reflectiveQuadToRelative(1.07f, 0.73f)
                lineToRelative(-2.3f, 2.3f)
                lineTo(11f, 7.5f)
                lineTo(10.3f, 8.2f)
                lineTo(12f, 9.9f)
                lineTo(14.25f, 7.65f)
                quadTo(14.15f, 7.38f, 14.09f, 7.07f)
                reflectiveQuadTo(14.03f, 6.47f)
                quadTo(14.03f, 5f, 15.04f, 3.99f)
                reflectiveQuadTo(17.53f, 2.97f)
                quadToRelative(0.38f, 0f, 0.71f, 0.07f)
                quadToRelative(0.34f, 0.08f, 0.69f, 0.23f)
                lineTo(16.45f, 5.75f)
                lineToRelative(1.8f, 1.8f)
                lineTo(20.73f, 5.07f)
                quadToRelative(0.18f, 0.35f, 0.24f, 0.69f)
                reflectiveQuadToRelative(0.06f, 0.71f)
                quadToRelative(0f, 1.48f, -1.01f, 2.49f)
                quadTo(19f, 9.98f, 17.53f, 9.98f)
                quadToRelative(-0.3f, 0f, -0.6f, -0.05f)
                quadTo(16.63f, 9.88f, 16.35f, 9.75f)
                lineTo(5.1f, 21f)
                close()
            }
        }.build()
    }

    val Experiment: ImageVector by lazy {
        ImageVector.Builder(
            name = "experiment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5f, 21f)
                quadTo(3.73f, 21f, 3.19f, 19.86f)
                reflectiveQuadTo(3.45f, 17.75f)
                lineTo(9f, 11f)
                verticalLineTo(5f)
                horizontalLineTo(8f)
                quadTo(7.58f, 5f, 7.29f, 4.71f)
                reflectiveQuadTo(7f, 4f)
                quadTo(7f, 3.57f, 7.29f, 3.29f)
                reflectiveQuadTo(8f, 3f)
                horizontalLineToRelative(8f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(17f, 4f)
                quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                reflectiveQuadTo(16f, 5f)
                horizontalLineTo(15f)
                verticalLineToRelative(6f)
                lineToRelative(5.55f, 6.75f)
                quadToRelative(0.8f, 0.98f, 0.26f, 2.11f)
                quadTo(20.28f, 21f, 19f, 21f)
                horizontalLineTo(5f)
                close()
                moveTo(7f, 18f)
                horizontalLineTo(17f)
                lineTo(13.6f, 14f)
                horizontalLineTo(10.4f)
                lineTo(7f, 18f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(19f)
                lineTo(13f, 11.7f)
                verticalLineTo(5f)
                horizontalLineTo(11f)
                verticalLineToRelative(6.7f)
                lineTo(5f, 19f)
                close()
            }
        }.build()
    }
}
