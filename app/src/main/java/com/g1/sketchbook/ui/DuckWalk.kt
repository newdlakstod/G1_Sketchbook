package com.g1.sketchbook.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.g1.sketchbook.R

/**
 * The onboarding walking-duck as an animated GIF (res/raw/duck_walk.gif), played via Coil's
 * GIF decoder — `painterResource` only shows a static first frame, so we decode + animate here.
 */
@Composable
fun DuckWalk(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(R.raw.duck_walk).build(),
        imageLoader = loader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
