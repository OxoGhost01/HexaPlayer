package com.oxoghost.hexaplayer.ui.material.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Shape

/** Corner radii from tokens.json > material.shape_dp. */
object HexaShapes {
    val navBar = RoundedCornerShape(Shape.navBar.dp)
    val miniPlayer = RoundedCornerShape(Shape.miniPlayer.dp)
    val card = RoundedCornerShape(Shape.card.dp)
    val sheetTop = RoundedCornerShape(topStart = Shape.sheetTop.dp, topEnd = Shape.sheetTop.dp)
    val dialog = RoundedCornerShape(Shape.dialog.dp)
    val pill = CircleShape
    val chip = RoundedCornerShape(Shape.chip.dp)
    val tabChip = RoundedCornerShape(Shape.tabChip.dp)
    val textField = RoundedCornerShape(Shape.textField.dp)
    val coverList = RoundedCornerShape(Shape.coverList.dp)
    val coverRow = RoundedCornerShape(Shape.coverRow.dp)
    val coverGrid = RoundedCornerShape(Shape.coverGrid.dp)
    val coverPlayer = RoundedCornerShape(Shape.coverPlayer.dp)
    val coverAlbumHeader = RoundedCornerShape(Shape.coverAlbumHeader.dp)
    val playButton = RoundedCornerShape(Shape.playButton.dp)
    val skipButton = RoundedCornerShape(Shape.skipButton.dp)

    val m3 = Shapes(
        extraSmall = RoundedCornerShape(Shape.groupInner.dp),
        small = chip,
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(Shape.groupOuter.dp),
        extraLarge = card,
    )
}
