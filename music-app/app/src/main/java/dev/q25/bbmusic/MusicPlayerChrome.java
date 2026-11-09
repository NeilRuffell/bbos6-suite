package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.ImageView;

/**
 * Resource-independent visual port of BB UI's expanded BBOS6 player chrome.
 * Color stops, geometry, dimensions and layer order match TwoColumnPanel
 * and UnifiedShadeHost (master), without SystemUI or Vector dependencies.
 */
final class MusicPlayerChrome {
    static final int BLUE_BORDER = 0xffb4e2fb;
    private static final int TRACK_TOP = 0xff101010;
    private static final int TRACK_BOTTOM = 0xff393839;

    private MusicPlayerChrome() {}

    static int dp(Context context, float value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static Drawable seekBackground(Context context) {
        LayerDrawable layers = new LayerDrawable(new Drawable[] {
                MediaChromeDivider.darkMediaBackground(),
                MediaChromeDivider.darkRule(),
                MediaChromeDivider.silverLine()
        });
        layers.setLayerHeight(1, dp(context, MediaChromeDivider.DARK_RULE_HEIGHT_DP));
        layers.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        layers.setLayerHeight(2, dp(context, MediaChromeDivider.HEIGHT_DP));
        layers.setLayerGravity(2, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        return layers;
    }

    static Drawable modeHeaderBackground(Context context) {
        LayerDrawable layers = new LayerDrawable(new Drawable[] {
                MediaChromeDivider.darkMediaBackground(),
                MediaChromeDivider.silverLine(),
                MediaChromeDivider.darkRule()
        });
        layers.setLayerHeight(1, dp(context, MediaChromeDivider.HEIGHT_DP));
        layers.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        layers.setLayerHeight(2, dp(context, MediaChromeDivider.DARK_RULE_HEIGHT_DP));
        layers.setLayerGravity(2, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        return layers;
    }

    static Drawable seekTrack(Context context) {
        final int railHeight = dp(context, 4);
        Drawable base = new Drawable() {
            private final Paint top = new Paint();
            private final Paint bottom = new Paint();

            {
                top.setColor(TRACK_TOP);
                bottom.setColor(TRACK_BOTTOM);
            }

            @Override public void draw(Canvas canvas) {
                Rect rect = getBounds();
                int y = rect.centerY() - railHeight / 2;
                canvas.drawRect(rect.left, y, rect.right, y + railHeight / 2, top);
                canvas.drawRect(rect.left, y + railHeight / 2,
                        rect.right, y + railHeight, bottom);
            }
            @Override public int getIntrinsicHeight() { return railHeight; }
            @Override public void setAlpha(int alpha) {
                top.setAlpha(alpha);
                bottom.setAlpha(alpha);
                invalidateSelf();
            }
            @Override public void setColorFilter(ColorFilter filter) {
                top.setColorFilter(filter);
                bottom.setColorFilter(filter);
                invalidateSelf();
            }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };

        ClipDrawable played = new ClipDrawable(new ColorDrawable(Color.WHITE),
                Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable result = new LayerDrawable(new Drawable[] {base, played});
        result.setId(0, android.R.id.background);
        result.setId(1, android.R.id.progress);
        result.setLayerHeight(1, railHeight);
        result.setLayerGravity(1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        return result;
    }

    static Drawable seekThumb(Context context, boolean highlighted, boolean selected) {
        if (selected) {
            GradientDrawable pill = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            0xff088eef, 0xff088aef, 0xff0886ef,
                            0xff0882ef, 0xff087de7, 0xff0879e7,
                            0xff1075e7, 0xff1071e7, 0xff106dde,
                            0xff1069de, 0xff1065de
                    });
            pill.setCornerRadius(dp(context, 10));
            pill.setSize(dp(context, 56), dp(context, 18));
            pill.setStroke(dp(context, 2), BLUE_BORDER);
            return pill;
        }
        GradientDrawable shell = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                highlighted
                        ? new int[] {0xff19466e, 0xff477eac, 0xff12365c}
                        : new int[] {0xff636563, 0xff929396, 0xff525253});
        shell.setCornerRadius(dp(context, 8));
        shell.setSize(dp(context, 54), dp(context, 16));
        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                highlighted
                        ? new int[] {
                            0xffe7f6ff, 0xff4b92d6, 0xff559cdf,
                            0xff64a7e6, 0xff7cb8ef, 0xff92c8f6,
                            0xffbadeff, 0xffe1f1ff, 0xff578fbe
                          }
                        : new int[] {
                            0xfff7f7f7, 0xff8ca6c6, 0xff9caece,
                            0xffa5bace, 0xffbdcbde, 0xffced7e7,
                            0xffdee3ef, 0xffefeff7, 0xffadaaad
                          });
        body.setCornerRadius(dp(context, 7));
        body.setSize(dp(context, 52), dp(context, 14));
        LayerDrawable layered = new LayerDrawable(new Drawable[] {shell, body});
        layered.setLayerInset(1, dp(context, 1), dp(context, 1),
                dp(context, 1), dp(context, 1));
        return layered;
    }

    // Pixel rows 1..59 of the supplied 480x60 BBOS6 media-control reference
    // (row 0 is the silver seek/transport divider, already drawn elsewhere).
    // The indexed PNG has discrete palette bands. A five-stop interpolated
    // GradientDrawable loses those characteristic horizontal steps.
    private static final int[] TRANSPORT_REFERENCE_ROWS = {
            0xff6b6d73, 0xff6b6d73, 0xff6b6d73, 0xff636973, 0xff63696b, 0xff63656b,
            0xff5a656b, 0xff5a6163, 0xff5a6163, 0xff5a5d63, 0xff52595a, 0xff52595a,
            0xff52555a, 0xff52555a, 0xff4a515a, 0xff4a5152, 0xff4a4d52, 0xff424d52,
            0xff42494a, 0xff42454a, 0xff42454a, 0xff394142, 0xff394142, 0xff393c42,
            0xff393c42, 0xff393c42, 0xff313842, 0xff313839, 0xff313842, 0xff313839,
            0xff313839, 0xff313439, 0xff313439, 0xff313439, 0xff313439, 0xff313031,
            0xff293031, 0xff313031, 0xff293031, 0xff293031, 0xff292c31, 0xff292c31,
            0xff292c31, 0xff292c31, 0xff292c31, 0xff292829, 0xff212829, 0xff292829,
            0xff292829, 0xff212829, 0xff212429, 0xff212429, 0xff212829, 0xff212429,
            0xff212429, 0xff212429, 0xff212421, 0xff212021, 0xff212421
    };

    // Measured against the Q25 capture: both transport and other player chrome
    // are brightened by approximately 12% white relative to the authored
    // opaque colours. The source of this whole-window lift is not established.
    // Compensate *only this sampled background* for the currently observed Q25
    // rendering; separators, selected button and other chrome are unchanged.
    private static final float TRANSPORT_SCREEN_WHITE_LIFT = 0.12f;

    private static int transportSourceChannel(int reference) {
        float lift = TRANSPORT_SCREEN_WHITE_LIFT;
        return Math.max(0, Math.min(255,
                Math.round((reference - 255f * lift) / (1f - lift))));
    }

    private static int transportSourceColor(int reference) {
        return Color.rgb(
                transportSourceChannel(Color.red(reference)),
                transportSourceChannel(Color.green(reference)),
                transportSourceChannel(Color.blue(reference)));
    }

    static Drawable transportBackground() {
        return new Drawable() {
            private final Paint paint = new Paint();
            private final int[] sourceRows = new int[TRANSPORT_REFERENCE_ROWS.length];
            private int alpha = 255;

            {
                // No bitmap asset, and no interpolation/dithering between
                // reference palette rows: scale the bands by screen height.
                paint.setAntiAlias(false);
                paint.setDither(false);
                for (int i = 0; i < sourceRows.length; i++) {
                    sourceRows[i] = transportSourceColor(TRANSPORT_REFERENCE_ROWS[i]);
                }
            }

            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                int height = bounds.height();
                if (height <= 0) return;
                for (int y = bounds.top; y < bounds.bottom; y++) {
                    int sample = (int) ((long) (y - bounds.top)
                            * sourceRows.length / height);
                    paint.setColor(sourceRows[sample]);
                    paint.setAlpha(alpha);
                    canvas.drawRect(bounds.left, y, bounds.right, y + 1, paint);
                }
            }

            @Override public void setAlpha(int value) {
                alpha = value;
                invalidateSelf();
            }
            @Override public void setColorFilter(ColorFilter filter) {
                paint.setColorFilter(filter);
                invalidateSelf();
            }
            @Override public int getOpacity() {
                return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
            }
        };
    }

    static GradientDrawable transportSelection() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        0xff0871ce, 0xff0871ce, 0xff086dc6,
                        0xff086dc6, 0xff0869bd, 0xff0865bd,
                        0xff0861b5, 0xff105db5, 0xff1059ad,
                        0xff1055a5, 0xff1055a5
                });
    }

    static Drawable transportSeparators(Context context, ImageView[] buttons) {
        return new Drawable() {
            private final Paint dark = new Paint();
            private final Paint light = new Paint();

            @Override protected void onBoundsChange(Rect bounds) {
                super.onBoundsChange(bounds);
                if (bounds.isEmpty()) return;
                float[] stops = {0f, 0.17f, 0.34f, 0.51f, 1f};
                dark.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                        new int[] {
                                0xff525152, 0xff313839, 0xff212829,
                                0xff181c18, 0xff080808
                        }, stops, Shader.TileMode.CLAMP));
                light.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                        new int[] {
                                0xffb5b2b5, 0xff9c9ea5, 0xff848684,
                                0xff6b6d73, 0xff313831
                        }, stops, Shader.TileMode.CLAMP));
            }

            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                int width = dp(context, 1);
                for (int i = 0; i < buttons.length - 1; i++) {
                    int edge = buttons[i].getRight();
                    if (edge <= bounds.left || edge >= bounds.right) continue;
                    canvas.drawRect(edge - width, bounds.top, edge, bounds.bottom, dark);
                    canvas.drawRect(edge, bounds.top, edge + width, bounds.bottom, light);
                }
            }
            @Override public void setAlpha(int alpha) {
                dark.setAlpha(alpha);
                light.setAlpha(alpha);
                invalidateSelf();
            }
            @Override public void setColorFilter(ColorFilter filter) {
                dark.setColorFilter(filter);
                light.setColorFilter(filter);
                invalidateSelf();
            }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    static Drawable transportIcon(Context context, int action, boolean pause) {
        Path shape = new Path();
        if (action == 0) {
            shape.addRect(5f, 5f, 7f, 19f, Path.Direction.CW);
            shape.moveTo(18f, 5f);
            shape.lineTo(8f, 12f);
            shape.lineTo(18f, 19f);
            shape.close();
        } else if (action == 3) {
            shape.moveTo(6f, 5f);
            shape.lineTo(16f, 12f);
            shape.lineTo(6f, 19f);
            shape.close();
            shape.addRect(17f, 5f, 19f, 19f, Path.Direction.CW);
        } else if (action == 2) {
            // Standard square Stop symbol, using the same 24-unit canvas
            // and white transport-icon styling as Play and Previous/Next.
            shape.addRect(6f, 6f, 18f, 18f, Path.Direction.CW);
        } else if (pause) {
            shape.addRect(6f, 5f, 10f, 19f, Path.Direction.CW);
            shape.addRect(14f, 5f, 18f, 19f, Path.Direction.CW);
        } else {
            shape.moveTo(7f, 5f);
            shape.lineTo(19f, 12f);
            shape.lineTo(7f, 19f);
            shape.close();
        }

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        return new Drawable() {
            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                if (bounds.isEmpty()) return;
                int save = canvas.save();
                canvas.translate(bounds.left, bounds.top);
                canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
                canvas.drawPath(shape, fill);
                canvas.restoreToCount(save);
            }
            @Override public int getIntrinsicWidth() { return dp(context, 36); }
            @Override public int getIntrinsicHeight() { return dp(context, 36); }
            @Override public void setAlpha(int alpha) {
                fill.setAlpha(alpha);
                invalidateSelf();
            }
            @Override public void setColorFilter(ColorFilter filter) {
                fill.setColorFilter(filter);
                invalidateSelf();
            }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

}
