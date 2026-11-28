package com.memora.app.ui.components

/**
 * The one canonical Topilma card scene used by the live app card, Android Share
 * and Obsidian. All values are expressed on a 1536 px reference-width canvas.
 *
 * Width never changes. The 1199 px reference height is only a minimum: when the
 * complete quote (normally <= 100–150 words) needs more room, the body, footer,
 * card and the active Memora outer canvas extend downward together. Text is never split into
 * several cards and is never shrunk to force it into a landscape frame.
 */
object TopilmaCardVisualSpec {
    const val CANVAS_WIDTH = 1536
    const val MIN_CANVAS_HEIGHT = 1199

    const val CARD_BACKGROUND_ARGB: Long = 0xFFFFFFFFL

    // White card frame measured from the approved 1016 x 793 reference.
    const val CARD_LEFT = 82f
    const val CARD_TOP = 84f
    const val CARD_RIGHT = 1451f
    const val CARD_RADIUS = 72f
    const val ACTION_TO_CARD_BOTTOM = 126f
    const val CARD_TO_CANVAS_BOTTOM = 87f

    // Source cover and header.
    const val COVER_LEFT = 163f
    const val COVER_TOP = 169f
    const val COVER_WIDTH = 148f
    const val COVER_HEIGHT = 218f
    const val COVER_RADIUS = 11f
    const val COVER_BORDER = 2f
    const val COVER_INSET = 4f

    const val TITLE_LEFT = 381f
    const val TITLE_TOP = 166f
    const val TITLE_RIGHT = 1296f
    const val TITLE_TEXT_SIZE = 64f
    const val TITLE_LINE_HEIGHT = 75f
    const val AUTHOR_TEXT_SIZE = 48f
    const val AUTHOR_LINE_HEIGHT = 57f
    const val AUTHOR_TOP_GAP = 7f

    // Horizontal three-dot menu.
    const val MENU_CENTER_X = 1331f
    const val MENU_CENTER_Y = 231f
    const val MENU_DOT_RADIUS = 6f
    const val MENU_DOT_GAP = 20f

    // Full quote. Font size and width are fixed; height is content-driven.
    const val BODY_LEFT = 163f
    const val BODY_TOP = 476f
    const val BODY_RIGHT = 1373f
    const val BODY_TEXT_SIZE = 64f
    const val BODY_LINE_HEIGHT = 94f
    const val BODY_TO_FOOTER_GAP = 126f

    // Hashtags, when present, extend the same card rather than stealing body room.
    const val BODY_TO_TAGS_GAP = 42f
    const val TAG_TEXT_SIZE = 23f
    const val TAG_HORIZONTAL_PADDING = 16f
    const val TAG_VERTICAL_PADDING = 8f
    const val TAG_GAP_X = 12f
    const val TAG_GAP_Y = 8f
    const val TAGS_TO_FOOTER_GAP = 82f

    // Reference footer. It moves down by exactly the body overflow amount.
    const val BASE_ACTION_CENTER_Y = 986f
    const val SHARE_CENTER_X = 258f
    const val LIKE_CENTER_X = 768f
    const val COMMENT_CENTER_X = 1285f
    // 90 px source crop on the approved 1016 px card reference, scaled to the
    // canonical 1536 px renderer. The same VectorDrawable provides all glyphs.
    const val ACTION_ICON_BOX_SIZE = 136f
    const val COUNT_TEXT_SIZE = 32f

    fun actionCenterForCanvas(canvasHeight: Int): Float =
        canvasHeight - CARD_TO_CANVAS_BOTTOM - ACTION_TO_CARD_BOTTOM
}
