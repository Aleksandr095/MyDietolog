package ru.mydietolog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AppIcons {
    val back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val left: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val right: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val add: ImageVector get() = Icons.Filled.Add
    val check: ImageVector get() = Icons.Filled.Check
    val close: ImageVector get() = Icons.Filled.Close
    val calendar: ImageVector get() = Icons.Filled.DateRange
    val delete: ImageVector get() = Icons.Filled.Delete
    val info: ImageVector get() = Icons.Filled.Info
    val share: ImageVector get() = Icons.Filled.Share

    val image: ImageVector by lazy {
        stroked("image", "M5,4h14a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-12a2,2 0 0 1 2,-2z M11,10a2,2 0 1 1 -4,0a2,2 0 1 1 4,0z M21,17l-5,-5l-9,8")
    }
    val camera: ImageVector by lazy {
        stroked("camera", "M4,8h3l2,-3h6l2,3h3v11h-16z M15.5,13a3.5,3.5 0 1 1 -7,0a3.5,3.5 0 1 1 7,0z")
    }
    val download: ImageVector by lazy {
        stroked("download", "M12,3v12 M7,10l5,5l5,-5 M5,21h14")
    }
    val clock: ImageVector by lazy {
        stroked("clock", "M21,12a9,9 0 1 1 -18,0a9,9 0 1 1 18,0z M12,7v5l3,2")
    }

    private fun stroked(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
