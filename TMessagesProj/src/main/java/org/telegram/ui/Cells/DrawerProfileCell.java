/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.palette.graphics.Palette;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiTextView;
import org.telegram.ui.Components.FireworksEffect;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.SnowflakesEffect;

import tw.nekomimi.nkmr.NekomuraConfig;
import tw.nekomimi.nkmr.NekomuraConfig;

public class DrawerProfileCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private AudioPlayerAlert.ClippingTextViewSwitcher phoneTextView;
    private ImageView shadowView;
    protected ImageView arrowView;
    private final ImageReceiver imageReceiver;
    private RLottieImageView darkThemeView;
    private RLottieDrawable sunDrawable;

    private Rect srcRect = new Rect();
    private Rect destRect = new Rect();
    private Paint paint = new Paint();
    private Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Integer currentColor;
    private Integer currentMoonColor;
    private SnowflakesEffect snowflakesEffect;
    private FireworksEffect fireworksEffect;
    private boolean accountsShown;
    private int darkThemeBackgroundColor;
    public static boolean switchingTheme;

    private Bitmap lastBitmap;
    private TLRPC.User user;
    private boolean allowInvalidate = true;

    public DrawerProfileCell(Context context) {
        super(context);

        imageReceiver = new ImageReceiver(this);
        imageReceiver.setCrossfadeWithOldImage(true);
        imageReceiver.setForceCrossfade(true);
        imageReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (NekomuraConfig.avatarBackgroundDarken.Bool() || NekomuraConfig.avatarBackgroundBlur.Bool()) {
                if (thumb || allowInvalidate) {
                    return;
                }
                ImageReceiver.BitmapHolder bmp = imageReceiver.getBitmapSafe();
                if (bmp != null) {
                    new Thread(() -> {
                        if (lastBitmap != null) {
                            imageReceiver.setCrossfadeWithOldImage(false);
                            imageReceiver.setImageBitmap(new BitmapDrawable(null, lastBitmap), false);
                        }
                        int width = NekomuraConfig.avatarBackgroundBlur.Bool() ? 150 : bmp.bitmap.getWidth();
                        int height = NekomuraConfig.avatarBackgroundBlur.Bool() ? 150 : bmp.bitmap.getHeight();
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        canvas.drawBitmap(bmp.bitmap, null, new Rect(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG));
                        if (NekomuraConfig.avatarBackgroundBlur.Bool()) {
                            try {
                                Utilities.stackBlurBitmap(bitmap, 3);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        if (NekomuraConfig.avatarBackgroundDarken.Bool()) {
                            final Palette palette = Palette.from(bmp.bitmap).generate();
                            Paint paint = new Paint();
                            paint.setColor((palette.getDarkMutedColor(0xFF547499) & 0x00FFFFFF) | 0x44000000);
                            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            allowInvalidate = true;
                            imageReceiver.setCrossfadeWithOldImage(true);
                            imageReceiver.setImageBitmap(new BitmapDrawable(null, bitmap), false);
                            lastBitmap = bitmap;
                        });
                    }).start();
                }
            } else {
                lastBitmap = null;
            }
        });

        shadowView = new ImageView(context);
        shadowView.setVisibility(INVISIBLE);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.bottom_shadow);
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM));

        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(32));
        addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));

        nameTextView = new EmojiTextView(context);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 76, 28));

        phoneTextView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                textView.setLines(1);
                textView.setMaxLines(1);
                textView.setSingleLine(true);
                textView.setGravity(Gravity.LEFT);
                return textView;
            }
        };
        addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 76, 9));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.menu_expand);
        addView(arrowView, LayoutHelper.createFrame(59, 59, Gravity.RIGHT | Gravity.BOTTOM));
        setArrowState(false);

        sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        if (isCurrentThemeDay()) {
            sunDrawable.setCustomEndFrame(36);
        } else {
            sunDrawable.setCustomEndFrame(0);
            sunDrawable.setCurrentFrame(36);
        }
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        darkThemeView = new RLottieImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (sunDrawable.getCustomEndFrame() != 0) {
                    info.setText(LocaleController.getString("AccDescrSwitchToNightTheme", R.string.AccDescrSwitchToNightTheme));
                } else {
                    info.setText(LocaleController.getString("AccDescrSwitchToDayTheme", R.string.AccDescrSwitchToDayTheme));
                }
            }
        };
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        sunDrawable.commitApplyLayerColors();
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setAnimation(sunDrawable);
        if (Build.VERSION.SDK_INT >= 21) {
            darkThemeView.setBackgroundDrawable(Theme.createSelectorDrawable(darkThemeBackgroundColor = Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(17)));
            Theme.setRippleDrawableForceSoftware((RippleDrawable) darkThemeView.getBackground());
        }
        darkThemeView.setOnClickListener(v -> {
            if (switchingTheme) {
                return;
            }
            switchingTheme = true;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null) {
                dayThemeName = "Blue";
            }
            String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null) {
                nightThemeName = "Dark Blue";
            }
            Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }

            boolean toDark;
            if (toDark = dayThemeName.equals(themeInfo.getKey())) {
                themeInfo = Theme.getTheme(nightThemeName);
                sunDrawable.setCustomEndFrame(36);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
                sunDrawable.setCustomEndFrame(0);
            }
            darkThemeView.playAnimation();
            if (Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE) {
                Toast.makeText(getContext(), LocaleController.getString("AutoNightModeOff", R.string.AutoNightModeOff), Toast.LENGTH_SHORT).show();
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                Theme.saveAutoNightThemeConfig();
                Theme.cancelAutoNightThemeCallbacks();
            }
            switchTheme(themeInfo, toDark);
        });

        LayoutParams lp = NekomuraConfig.largeAvatarInDrawer.Int() == 2 ? // correct the position of this button
                LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 10, 6, 0) :
                LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 10, 6, 90);
        addView(darkThemeView, lp);

        if (NekomuraConfig.largeAvatarInDrawer.Int() == 2) { // add shadow
            nameTextView.setShadowLayer(6.0f, 2.0f, 2.0f, Color.BLACK);
            phoneTextView.getTextView().setShadowLayer(6.0f, 2.0f, 2.0f, Color.BLACK);
        }

        if (Theme.getEventType() == 0 || NekomuraConfig.actionBarDecoration.Int() == 1) {
            snowflakesEffect = new SnowflakesEffect();
            snowflakesEffect.setColorKey(Theme.key_chats_menuName);
        } else if (NekomuraConfig.actionBarDecoration.Int() == 2) {
            fireworksEffect = new FireworksEffect();
        }
    }

    private void switchTheme(Theme.ThemeInfo themeInfo, boolean toDark) {
        int[] pos = new int[2];
        darkThemeView.getLocationInWindow(pos);
        pos[0] += darkThemeView.getMeasuredWidth() / 2;
        pos[1] += darkThemeView.getMeasuredHeight() / 2;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, darkThemeView);
    }

    private boolean isCurrentThemeDay() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName) && themeInfo.isDark()) {
            dayThemeName = "Blue";
        }
        return dayThemeName.equals(themeInfo.getKey());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightBase = NekomuraConfig.largeAvatarInDrawer.Int() == 2 ? MeasureSpec.getSize(widthMeasureSpec) : AndroidUtilities.dp(148);
        if (Build.VERSION.SDK_INT >= 21) {
            heightBase -= NekomuraConfig.largeAvatarInDrawer.Int() == 2 ? AndroidUtilities.statusBarHeight : 0;
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightBase + AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
        } else {
            try {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightBase, MeasureSpec.EXACTLY));
            } catch (Exception e) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightBase);
                FileLog.e(e);
            }
        }
    }

    private boolean useAdb() {
        return NekomuraConfig.largeAvatarInDrawer.Int() > 0 && ImageLocation.isUserHasPhoto(user);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable backgroundDrawable = Theme.getCachedWallpaper();
        String backgroundKey = applyBackground(false);
        boolean useImageBackground = !backgroundKey.equals(Theme.key_chats_menuTopBackground) && Theme.isCustomTheme() && !Theme.isPatternWallpaper() && backgroundDrawable != null && !(backgroundDrawable instanceof ColorDrawable) && !(backgroundDrawable instanceof GradientDrawable);
        boolean drawCatsShadow = false;
        int color;
        if (!useAdb() && !useImageBackground && Theme.hasThemeKey(Theme.key_chats_menuTopShadowCats)) {
            color = Theme.getColor(Theme.key_chats_menuTopShadowCats);
            drawCatsShadow = true;
        } else {
            if (Theme.hasThemeKey(Theme.key_chats_menuTopShadow)) {
                color = Theme.getColor(Theme.key_chats_menuTopShadow);
            } else {
                color = Theme.getServiceMessageColor() | 0xff000000;
            }
        }
        if (currentColor == null || currentColor != color) {
            currentColor = color;
            shadowView.getDrawable().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
        nameTextView.setTextColor(Theme.getColor(Theme.key_chats_menuName));
        phoneTextView.getTextView().setTextColor(Theme.getColor(Theme.key_chats_menuName));

        if (useAdb()) {
            phoneTextView.getTextView().setTextColor(Theme.getColor(Theme.key_chats_menuPhone));
            if (shadowView.getVisibility() != VISIBLE) {
                shadowView.setVisibility(VISIBLE);
            }
            imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            imageReceiver.draw(canvas);
        } else if (useImageBackground) {
            phoneTextView.getTextView().setTextColor(Theme.getColor(Theme.key_chats_menuPhone));
            if (shadowView.getVisibility() != VISIBLE) {
                shadowView.setVisibility(VISIBLE);
            }
            if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
                float scaleX = (float) getMeasuredWidth() / (float) bitmap.getWidth();
                float scaleY = (float) getMeasuredHeight() / (float) bitmap.getHeight();
                float scale = Math.max(scaleX, scaleY);
                int width = (int) (getMeasuredWidth() / scale);
                int height = (int) (getMeasuredHeight() / scale);
                int x = (bitmap.getWidth() - width) / 2;
                int y = (bitmap.getHeight() - height) / 2;
                srcRect.set(x, y, x + width, y + height);
                destRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                try {
                    canvas.drawBitmap(bitmap, srcRect, destRect, paint);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        } else {
            int visibility = drawCatsShadow ? VISIBLE : INVISIBLE;
            if (shadowView.getVisibility() != visibility) {
                shadowView.setVisibility(visibility);
            }
            super.onDraw(canvas);
        }

        if (snowflakesEffect != null) {
            snowflakesEffect.onDraw(this, canvas);
        } else if (fireworksEffect != null) {
            fireworksEffect.onDraw(this, canvas);
        }
    }

    public boolean isInAvatar(float x, float y) {
        if (useAdb()) {
            return y <= arrowView.getTop();
        } else {
            return x >= avatarImageView.getLeft() && x <= avatarImageView.getRight() && y >= avatarImageView.getTop() && y <= avatarImageView.getBottom();
        }
    }

    public boolean hasAvatar() {
        return avatarImageView.getImageReceiver().hasNotThumb();
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value) {
            return;
        }
        accountsShown = value;
        setArrowState(animated);
    }

    public void setUser(TLRPC.User user, boolean accounts) {
        if (user == null) {
            return;
        }
        this.user = user;
        accountsShown = accounts;
        setArrowState(false);
        nameTextView.setText(UserObject.getUserName(user));
        if (!NekomuraConfig.hidePhone.Bool()) {
            phoneTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        } else if (!TextUtils.isEmpty(user.username)) {
            phoneTextView.setText("@" + user.username);
        } else {
            phoneTextView.setText(LocaleController.getString("MobileHidden", R.string.MobileHidden));
        }
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundInProfileBlue));
        avatarImageView.setForUserOrChat(user, avatarDrawable);
        if (NekomuraConfig.largeAvatarInDrawer.Int() > 0) {
            ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_BIG);
            allowInvalidate = !useAdb() || !(NekomuraConfig.avatarBackgroundDarken.Bool() || NekomuraConfig.avatarBackgroundBlur.Bool());
            imageReceiver.setImage(imageLocation, "512_512", null, null, new ColorDrawable(0x00000000), 0, null, user, 1);
            avatarImageView.setVisibility(INVISIBLE);
        } else {
            avatarImageView.setVisibility(VISIBLE);
        }

        applyBackground(true);
    }

    public String applyBackground(boolean force) {
        String currentTag = (String) getTag();
        String backgroundKey = Theme.hasThemeKey(Theme.key_chats_menuTopBackground) && Theme.getColor(Theme.key_chats_menuTopBackground) != 0 ? Theme.key_chats_menuTopBackground : Theme.key_chats_menuTopBackgroundCats;
        if (force || !backgroundKey.equals(currentTag)) {
            setBackgroundColor(Theme.getColor(backgroundKey));
            setTag(backgroundKey);
        }
        return backgroundKey;
    }

    public void updateColors() {
        if (snowflakesEffect != null) {
            snowflakesEffect.updateColors();
        }
    }

    private void setArrowState(boolean animated) {
        final float rotation = accountsShown ? 180.0f : 0.0f;
        if (animated) {
            arrowView.animate().rotation(rotation).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        } else {
            arrowView.animate().cancel();
            arrowView.setRotation(rotation);
        }
        arrowView.setContentDescription(accountsShown ? LocaleController.getString("AccDescrHideAccounts", R.string.AccDescrHideAccounts) : LocaleController.getString("AccDescrShowAccounts", R.string.AccDescrShowAccounts));
    }

    @Override
    public void invalidate() {
        if (allowInvalidate) super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (allowInvalidate) super.invalidate(l, t, r, b);
    }
}