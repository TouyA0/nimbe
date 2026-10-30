package io.github.touya0.nimbe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.RowTitleStyle

/** Marge gauche et droite des écrans : titres, rangées et grilles s'y alignent. */
val ScreenMargin = 48.dp

/** Espace au-dessus et au-dessous des cartes d'une rangée : l'agrandissement et l'anneau du focus y tiennent. */
val RowCardPadding = 14.dp

/** Écart entre le titre d'une rangée et le haut de sa liste. */
private val RowTitleGap = 2.dp

/**
 * Du haut d'une rangée au haut de ses cartes, selon la taille du texte choisie. Les écrans qui empilent
 * des rangées s'en servent comme repère de défilement : la rangée focalisée vient se poser en haut, titre compris.
 */
@Composable
fun rowCardTop(): Dp = with(LocalDensity.current) { RowTitleStyle.lineHeight.toDp() } + RowTitleGap + RowCardPadding

/**
 * Rangée titrée de cartes, commune à tous les écrans : titre à la marge, cartes qui défilent sous
 * le focus resté à gauche (voir [PivotScroll]), anneau jamais coupé au bord de l'écran.
 * [count] s'affiche discrètement après le titre.
 */
@Composable
fun ContentRow(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    spacing: Dp = 16.dp,
    count: Int? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier) {
        RowTitle(title, count, Modifier.padding(bottom = RowTitleGap))
        PivotScroll(ScreenMargin) {
            LazyRow(
                state = state,
                modifier = Modifier.bleed(ScreenMargin),
                contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = RowCardPadding),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}

/** Titre d'une rangée, avec son nombre d'éléments en retrait. */
@Composable
fun RowTitle(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = RowTitleStyle, maxLines = 1)
        count?.takeIf { it > 0 }?.let { Text("$it", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee) }
    }
}

/** Une phrase à la place des cartes (chargement, vide, erreur), sans faire sauter l'écran. */
@Composable
fun RowMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = LuneAttenuee,
        modifier = modifier.padding(top = 6.dp, bottom = 18.dp),
    )
}
