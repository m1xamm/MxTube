package com.m1xamm.mxtube.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.m1xamm.mxtube.R

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_mark_cookie),
        contentDescription = null,
        modifier = modifier.size(38.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
    )
}
