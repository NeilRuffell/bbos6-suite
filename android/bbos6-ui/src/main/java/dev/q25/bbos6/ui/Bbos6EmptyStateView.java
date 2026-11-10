package dev.q25.bbos6.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Presentation-only empty state for any BBOS6-style content area.
 *
 * The host controls when to show it and supplies its own text and optional
 * artwork. This view is not an adapter row, selection target, or action.
 * Its background stays transparent so it can be placed beside a ListView
 * (or over its unused viewport) without obscuring the host styling.
 */
public final class Bbos6EmptyStateView extends LinearLayout {
    private static final int ARTWORK_DP = 144;
    private static final int ARTWORK_TO_TITLE_DP = 12;
    private static final int TITLE_TO_MESSAGE_DP = 16;
    private static final int SIDE_PADDING_DP = 22;

    private final ImageView artwork;
    private final TextView title;
    private final TextView message;

    public Bbos6EmptyStateView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(SIDE_PADDING_DP), dp(12), dp(SIDE_PADDING_DP), dp(12));
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);

        artwork = new ImageView(context);
        artwork.setFocusable(false);
        artwork.setClickable(false);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LayoutParams artParams = new LayoutParams(dp(ARTWORK_DP), dp(ARTWORK_DP));
        artParams.gravity = Gravity.CENTER_HORIZONTAL;
        artParams.bottomMargin = dp(ARTWORK_TO_TITLE_DP);
        addView(artwork, artParams);
        artwork.setVisibility(GONE);

        title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setFocusable(false);
        title.setClickable(false);
        title.setVisibility(GONE);
        LayoutParams titleParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(TITLE_TO_MESSAGE_DP);
        addView(title, titleParams);

        message = new TextView(context);
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        message.setFocusable(false);
        message.setClickable(false);
        message.setVisibility(GONE);
        addView(message, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Any field may be null or blank; no filler text or asset is invented. */
    public void setContent(CharSequence heading, CharSequence description) {
        title.setText(heading);
        title.setVisibility(isBlank(heading) ? GONE : VISIBLE);
        message.setText(description);
        message.setVisibility(isBlank(description) ? GONE : VISIBLE);
        updateSpacing();
    }

    /** Caller-provided drawable only; null clears the image entirely. */
    public void setArtwork(Drawable drawable) {
        artwork.setImageDrawable(drawable);
        artwork.setVisibility(drawable == null ? GONE : VISIBLE);
        updateSpacing();
    }

    /** Optional caller-owned drawable resource; zero removes artwork. */
    public void setArtworkResource(int drawableRes) {
        if (drawableRes == 0) {
            setArtwork(null);
        } else {
            setArtwork(getContext().getDrawable(drawableRes));
        }
    }

    private void updateSpacing() {
        LayoutParams artParams = (LayoutParams) artwork.getLayoutParams();
        artParams.bottomMargin = (title.getVisibility() == VISIBLE
                || message.getVisibility() == VISIBLE)
                ? dp(ARTWORK_TO_TITLE_DP) : 0;
        artwork.setLayoutParams(artParams);

        LayoutParams titleParams = (LayoutParams) title.getLayoutParams();
        titleParams.bottomMargin = message.getVisibility() == VISIBLE
                ? dp(TITLE_TO_MESSAGE_DP) : 0;
        title.setLayoutParams(titleParams);
    }

    private static boolean isBlank(CharSequence text) {
        return text == null || text.toString().trim().isEmpty();
    }

    private int dp(float size) {
        return Math.round(getResources().getDisplayMetrics().density * size);
    }
}
