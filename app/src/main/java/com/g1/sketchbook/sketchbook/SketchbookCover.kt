package com.g1.sketchbook.sketchbook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

// 표지 기본색: 나중에 별도 색상을 전달하지 않으면 모든 스케치북에 이 노란색이 적용됩니다.
val DefaultSketchbookCoverColor = Color(0xFFFFBF2A)

// 홈과 목록에서 같은 표지 모서리 모양을 사용합니다.
val SketchbookCoverShape = RoundedCornerShape(
    topEnd = 14.dp,
    bottomEnd = 14.dp,
    topStart = 4.dp,
    bottomStart = 4.dp,
)

// 책등 너비: 표지 전체 가로폭의 9%입니다.
private const val CoverSpineWidthFraction = 0.09f

// 단색 표지 책등: 선택한 표지색 위에 검정 20%를 겹쳐 같은 계열의 어두운 색을 만듭니다.
private const val SolidCoverSpineAlpha = 0.20f

// 이미지 표지 책등: 이미지 위에서도 확실히 보이도록 검정 70%를 겹칩니다.
private const val ImageCoverSpineAlpha = 0.70f

/**
 * 표지 색상·이미지·책등을 한곳에서 그리는 공용 컴포넌트입니다.
 * coverImage가 있으면 이미지를 우선 표시하고, content에는 제목이나 배지를 배치합니다.
 */
@Composable
fun SketchbookCover(
    modifier: Modifier = Modifier,
    coverColor: Color = DefaultSketchbookCoverColor,
    coverImage: Painter? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.clip(SketchbookCoverShape).background(coverColor)) {
        coverImage?.let { painter ->
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // 이미지가 있으면 70%, 단색이면 20% 검정 오버레이로 책등을 자동 조절합니다.
        val spineAlpha = if (coverImage == null) SolidCoverSpineAlpha else ImageCoverSpineAlpha
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(CoverSpineWidthFraction)
                .align(Alignment.CenterStart)
                .background(Color.Black.copy(alpha = spineAlpha)),
        )

        content()
    }
}
