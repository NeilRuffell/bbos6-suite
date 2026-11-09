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

    /**
     * Smooth interpretation of the 480x30 BBOS6 2486.png bottom-panel sample.
     * The seek strip owns BOTH its dark top highlight and silver lower edge.
     * Indexed-PNG palette steps are omitted, but its pronounced lower-centre
     * bevel is preserved. The media transport below draws no extra top rule.
     */
    static Drawable seekBackground(Context context) {
        GradientDrawable body = new GradientDrawable();
        body.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        body.setColors(new int[] {
                0xff212421, 0xff212021, 0xff181c18, 0xff181818,
                0xff181818
        }, new float[] {0f, 0.28f, 0.53f, 0.68f, 1f});
        body.setDither(true);

        // 2486.png row 0: muted dark highlight, strongest near the middle
        // and right half of the panel (unlike the silver bottom border).
        GradientDrawable topRule = new GradientDrawable();
        topRule.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        topRule.setColors(new int[] {
                0xff393839, 0xff4a494a, 0xff5a5d5a, 0xff5a6163
        }, new float[] {0f, 0.23f, 0.51f, 1f});

        // The 2486.png lower bevel brightens over its last four body rows,
        // especially at the horizontal centre. Reproduce its smooth 2D
        // falloff without the indexed source image's palette dither/noise.
        Drawable lowerBevel = new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int drawableAlpha = 255;

            @Override protected void onBoundsChange(Rect bounds) {
                super.onBoundsChange(bounds);
                if (bounds.isEmpty()) {
                    paint.setShader(null);
                    return;
                }
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.top,
                        new int[] {
                                0x00ffffff, 0x00ffffff, 0xffffffff,
                                0x00ffffff, 0x00ffffff
                        }, new float[] {0f, 0.21f, 0.50f, 0.79f, 1f},
                        Shader.TileMode.CLAMP));
            }

            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                if (bounds.isEmpty()) return;
                // Reserve the final physical dp for the separate silver rule.
                int end = bounds.bottom - dp(context, 1);
                int start = Math.max(bounds.top + dp(context, 1),
                        end - dp(context, 5));
                int height = end - start;
                if (height <= 0) return;

                for (int y = start; y < end; y++) {
                    float rise = (float) (y - start + 1) / height;
                    int alpha = Math.round(52f * (float) Math.pow(rise, 3.2)
                            * drawableAlpha / 255f);
                    paint.setAlpha(alpha);
                    canvas.drawRect(bounds.left, y, bounds.right, y + 1, paint);
                }
            }

            @Override public void setAlpha(int alpha) {
                drawableAlpha = alpha;
                invalidateSelf();
            }
            @Override public void setColorFilter(ColorFilter filter) {
                paint.setColorFilter(filter);
                invalidateSelf();
            }
            @Override public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };

        // 2486.png row 29: bright silver-to-white-to-silver separator.
        // This bottom rule belongs to the seekbar panel, not the buttons.
        GradientDrawable silverBottomRule = new GradientDrawable();
        silverBottomRule.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        silverBottomRule.setColors(new int[] {
                0xff949294, 0xfff7f7f7, 0xffffffff,
                0xfff7f7f7, 0xff9c9a9c
        }, new float[] {0f, 0.36f, 0.45f, 0.64f, 1f});

        LayerDrawable layers = new LayerDrawable(new Drawable[] {
                body, lowerBevel, topRule, silverBottomRule
        });
        layers.setLayerHeight(2, dp(context, 1));
        layers.setLayerGravity(2, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        layers.setLayerHeight(3, dp(context, 1));
        layers.setLayerGravity(3, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        return layers;
    }

    /**
     * The BBOS6 2513.png header reference uses a bright horizontal top
     * highlight, a short centre-brightened upper bevel, a dark vertical body,
     * and a faint lower bevel. Use smooth gradients for the underlying shape:
     * the small palette steps in the indexed PNG are not copied as UI bands.
     *
     * This header is independent of the seek strip's 2486.png treatment
     * and the Now Playing transport background; neither is modified here.
     */
    static Drawable modeHeaderBackground(Context context) {
        GradientDrawable body = new GradientDrawable();
        body.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        body.setColors(new int[] {
                0xff212021, 0xff212021, 0xff181818,
                0xff181818, 0xff101010, 0xff101010
        }, new float[] {0f, 0.20f, 0.48f, 0.67f, 0.90f, 1f});
        body.setDither(true);

        // Reference rows 1-7: a broad, shallow highlight across the upper
        // centre, fading out rapidly before the middle of the header.
        // A smoothly varying alpha replaces the PNG's palette quantization.
        Drawable upperBevel = new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int drawableAlpha = 255;

            @Override protected void onBoundsChange(Rect bounds) {
                super.onBoundsChange(bounds);
                if (bounds.isEmpty()) return;
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.top,
                        new int[] {
                                0x00ffffff, 0xffffffff, 0xffffffff, 0x00ffffff
                        },
                        new float[] {0f, 0.43f, 0.57f, 1f},
                        Shader.TileMode.CLAMP));
            }

            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                if (bounds.isEmpty()) return;
                int top = bounds.top + dp(context, 1);
                int bottom = Math.min(bounds.bottom - dp(context, 1),
                        top + dp(context, 7));
                int height = bottom - top;
                if (height <= 0) return;

                for (int y = top; y < bottom; y++) {
                    float progress = (y - top + 0.5f) / height;
                    float strength = (float) Math.pow(1f - progress, 1.55f);
                    paint.setAlpha(Math.round(42f * strength
                            * drawableAlpha / 255f));
                    canvas.drawRect(bounds.left, y, bounds.right, y + 1, paint);
                }
            }

            @Override public void setAlpha(int alpha) {
                drawableAlpha = alpha;
                invalidateSelf();
            }
            @Override public void setColorFilter(ColorFilter filter) {
                paint.setColorFilter(filter);
                invalidateSelf();
            }
            @Override public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };

        // 2513.png row 0: silver horizontal gradient with a white centre.
        GradientDrawable topHighlight = new GradientDrawable();
        topHighlight.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        topHighlight.setColors(new int[] {
                0xff9c9e9c, 0xffffffff, 0xffffffff, 0xffa5a2a5
        }, new float[] {0f, 0.45f, 0.55f, 1f});

        // Row 39: a much subtler lower bevel, not the former 2dp grey rule.
        GradientDrawable bottomBevel = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {
                        0xff212021, 0xff212421, 0xff212021
                });

        LayerDrawable layers = new LayerDrawable(new Drawable[] {
                body, upperBevel, topHighlight, bottomBevel
        });
        layers.setLayerHeight(2, dp(context, 1));
        layers.setLayerGravity(2, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        layers.setLayerHeight(3, dp(context, 1));
        layers.setLayerGravity(3, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
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

    // Reference 3246.png is an indexed-colour screenshot of BBOS6 Sound
    // Recorder controls. Its tiny repeated palette bands are quantization
    // artefacts, not evidence of intentionally stepped UI rendering.
    //
    // These stop colours/positions preserve the stronger falloff in the upper
    // third and flatter charcoal lower half without copying compression noise.
    private static final int[] TRANSPORT_SMOOTH_COLORS = {
            0xff6b6d73, // reference row 1: top, below the silver divider
            0xff63656b, // ~10%
            0xff5a5d63, // ~17%
            0xff4a515a, // ~25%
            0xff42454a, // ~34%
            0xff393c42, // ~42%
            0xff313839, // ~51%
            0xff293031, // ~67%
            0xff212829, // ~82%
            0xff212421  // bottom
    };
    private static final float[] TRANSPORT_SMOOTH_STOPS = {
            0f, 0.10f, 0.17f, 0.25f, 0.34f,
            0.42f, 0.51f, 0.67f, 0.82f, 1f
    };

    static GradientDrawable transportBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        drawable.setColors(TRANSPORT_SMOOTH_COLORS,
                TRANSPORT_SMOOTH_STOPS);
        drawable.setDither(true);
        return drawable;
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
