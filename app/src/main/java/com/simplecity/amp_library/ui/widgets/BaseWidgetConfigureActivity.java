package com.simplecity.amp_library.ui.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import com.afollestad.aesthetic.Aesthetic;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.simplecity.amp_library.R;
import com.simplecity.amp_library.model.Song;
import com.simplecity.amp_library.playback.MediaManager;
import com.simplecity.amp_library.playback.constants.MediaButtonCommand;
import com.simplecity.amp_library.playback.constants.ServiceCommand;
import com.simplecity.amp_library.ui.common.BaseActivity;
import com.simplecity.amp_library.ui.screens.widgets.WidgetFragment;
import com.simplecity.amp_library.ui.views.SizableSeekBar;
import com.simplecity.amp_library.utils.ColorUtils;
import dagger.android.AndroidInjection;
import javax.inject.Inject;

public abstract class BaseWidgetConfigureActivity extends BaseActivity implements
        View.OnClickListener,
        CheckBox.OnCheckedChangeListener,
        SeekBar.OnSeekBarChangeListener,
        ViewPager.OnPageChangeListener,
        ColorChooserDialog.ColorCallback {

    private ColorChooserDialog textColorDialog;
    private ColorChooserDialog backgroundColorDialog;

    abstract int[] getWidgetLayouts();

    abstract String getLayoutIdString();

    abstract String getUpdateCommandString();

    abstract int getRootViewId();

    int[] layouts;
    private int layoutId;
    private int appWidgetId;

    private float alpha = 0.15f;

    private ViewPager pager;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private WidgetPagerAdapter adapter;

    private SharedPreferences prefs;

    private Button backgroundColorButton;
    private Button textColorButton;
    

    private int backgroundColor;
    private int textColor;
    private boolean showAlbumArt;
    

    SparseArray<Fragment> registeredFragments = new SparseArray<>();

    @Inject
    MediaManager mediaManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SizableSeekBar seekBar;
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);

        if (Aesthetic.isFirstTime(this)) {
            Aesthetic.get(this)
                    .activityTheme(R.style.WallpaperTheme)
                    .isDark(false)
                    .colorPrimaryRes(R.color.md_blue_500)
                    .colorAccentRes(R.color.md_amber_300)
                    .colorStatusBarAuto()
                    .apply();
        }

        setContentView(R.layout.activity_widget_config);

        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        layoutId = prefs.getInt(getLayoutIdString() + appWidgetId, getWidgetLayouts()[0]);
        backgroundColor = prefs.getInt(BaseWidgetProvider.ARG_WIDGET_BACKGROUND_COLOR + appWidgetId, ContextCompat.getColor(this, R.color.white));
        textColor = prefs.getInt(BaseWidgetProvider.ARG_WIDGET_TEXT_COLOR + appWidgetId, Color.WHITE);
        showAlbumArt = prefs.getBoolean(BaseWidgetProvider.ARG_WIDGET_SHOW_ARTWORK + appWidgetId, true);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        layouts = getWidgetLayouts();

        // Instantiate a ViewPager and a PagerAdapter.
        pager = findViewById(R.id.pager);
        adapter = new WidgetPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(adapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(pager);
        pager.addOnPageChangeListener(this);

        Button doneButton = findViewById(R.id.btn_done);
        doneButton.setOnClickListener(this);

        backgroundColorButton = findViewById(R.id.btn_background_color);
        backgroundColorButton.setOnClickListener(this);

        textColorButton = findViewById(R.id.btn_text_color);
        textColorButton.setOnClickListener(this);

        CheckBox showAlbumArtCheckbox = findViewById(R.id.checkBox1);
        showAlbumArtCheckbox.setOnCheckedChangeListener(this);

        CheckBox invertedIconsCheckbox = findViewById(R.id.checkBox2);
        invertedIconsCheckbox.setOnCheckedChangeListener(this);

        seekBar = findViewById(R.id.seekBar1);
        seekBar.setOnSeekBarChangeListener(this);

        updateWidgetUI();
    }

    @Override
    public void onBackPressed() {
        if (pager.getCurrentItem() == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous step.
            pager.setCurrentItem(pager.getCurrentItem() - 1);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        boolean invertIcons;

        if (compoundButton.getId() == R.id.checkBox1) {
            showAlbumArt = checked;
            prefs.edit().putBoolean(BaseWidgetProvider.ARG_WIDGET_SHOW_ARTWORK + appWidgetId, showAlbumArt).apply();
        }
        if (compoundButton.getId() == R.id.checkBox2) {
            invertIcons = checked;
            prefs.edit().putBoolean(BaseWidgetProvider.ARG_WIDGET_INVERT_ICONS + appWidgetId, invertIcons).apply();
        }
        updateWidgetUI();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_done) {

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

            RemoteViews remoteViews = new RemoteViews(this.getPackageName(), layoutId);
            BaseWidgetProvider.setupButtons(this, remoteViews, appWidgetId, getRootViewId());
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);

            // Send broadcast intent to any running MediaPlaybackService so it can
            // wrap around with an immediate update.
            Intent updateIntent = new Intent(ServiceCommand.COMMAND);
            updateIntent.putExtra(MediaButtonCommand.CMD_NAME, getUpdateCommandString());
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { appWidgetId });
            updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            sendBroadcast(updateIntent);

            finish();
        }

        if (view.getId() == R.id.btn_background_color) {
            backgroundColorDialog = new ColorChooserDialog.Builder(this, R.string.color_pick)
                    .allowUserColorInputAlpha(true)
                    .show(getSupportFragmentManager());
        }
        if (view.getId() == R.id.btn_text_color) {
            textColorDialog = new ColorChooserDialog.Builder(this, R.string.color_pick)
                    .allowUserColorInputAlpha(true)
                    .show(getSupportFragmentManager());
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
        Fragment fragment = adapter.getRegisteredFragment(pager.getCurrentItem());
        if (fragment != null) {
            View view = fragment.getView();
            if (view != null) {
                View layout = view.findViewById(getRootViewId());
                alpha = 1 - (progress / 255f);
                int adjustedColor = ColorUtils.adjustAlpha(backgroundColor, alpha);
                layout.setBackgroundColor(adjustedColor);
                prefs.edit()
                        .putInt(BaseWidgetProvider.ARG_WIDGET_BACKGROUND_COLOR + appWidgetId, adjustedColor)
                        .apply();
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onPageScrolled(int i, float v, int i2) {

    }

    @Override
    public void onPageSelected(int position) {
        layoutId = layouts[position];
        prefs.edit().putInt(getLayoutIdString() + appWidgetId, layoutId).apply();
        updateWidgetUI();
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        updateWidgetUI();
        super.onServiceConnected(componentName, iBinder);
    }

    private class WidgetPagerAdapter extends FragmentStatePagerAdapter {

        public WidgetPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return WidgetFragment.newInstance(layouts[position]);
        }

        @Override
        public int getCount() {
            return layouts.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "Layout " + (position + 1);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        public Fragment getRegisteredFragment(int position) {
            return registeredFragments.get(position);
        }
    }

    public void updateWidgetUI() {
    backgroundColor = prefs.getInt(BaseWidgetProvider.ARG_WIDGET_BACKGROUND_COLOR + appWidgetId, ContextCompat.getColor(this, R.color.white));
    textColor = prefs.getInt(BaseWidgetProvider.ARG_WIDGET_TEXT_COLOR + appWidgetId, ContextCompat.getColor(this, R.color.white));

    setupButtonDrawable(backgroundColorButton, R.drawable.bg_rounded);
    setupButtonDrawable(textColorButton, R.drawable.bg_rounded);

    Fragment fragment = adapter.getRegisteredFragment(pager.getCurrentItem());
    if (fragment == null) return;

    View view = fragment.getView();
    if (view == null) return;

    View widgetLayout = view.findViewById(getRootViewId());
    widgetLayout.setBackgroundColor(ColorUtils.adjustAlpha(backgroundColor, alpha));

    updateTextFields(widgetLayout);
    updateAlbumArt(widgetLayout);
}

private void setupButtonDrawable(TextView button, int drawableRes) {
    Drawable drawable = DrawableCompat.wrap(ContextCompat.getDrawable(this, drawableRes));
    drawable.setBounds(0, 0, 60, 60);
    button.setCompoundDrawables(drawable, null, null, null);
}

private void updateTextFields(View layout) {
    TextView text1 = layout.findViewById(R.id.text1);
    TextView text2 = layout.findViewById(R.id.text2);
    TextView text3 = layout.findViewById(R.id.text3);

    Song song = mediaManager.getSong();
    if (song == null) return;

    if (text1 != null) {
        text1.setText(song.name);
        text1.setTextColor(textColor);
    }

    if (song.albumArtistName != null && song.albumName != null && text2 != null) {
        if (text3 == null) {
            text2.setText(song.albumArtistName + " • " + song.albumName);
            text2.setTextColor(textColor);
        } else {
            text2.setText(song.albumName);
            text2.setTextColor(textColor);
            text3.setText(song.albumArtistName);
            text3.setTextColor(textColor);
        }
    }
}

private void updateAlbumArt(View layout) {
    ImageView albumArt = layout.findViewById(R.id.album_art);
    if (albumArt == null) return;

    if (!showAlbumArt) {
        albumArt.setVisibility(View.GONE);
        return;
    }

    albumArt.setVisibility(View.VISIBLE);
    if (pager.getCurrentItem() == 1) {
        int colorFilterColor = ContextCompat.getColor(this, R.color.color_filter);
        albumArt.setColorFilter(colorFilterColor);
        prefs.edit().putInt(BaseWidgetProvider.ARG_WIDGET_COLOR_FILTER + appWidgetId, colorFilterColor).apply();
    } else {
        prefs.edit().putInt(BaseWidgetProvider.ARG_WIDGET_COLOR_FILTER + appWidgetId, -1).apply();
    }

    Glide.with(this)
        .load(mediaManager.getSong())
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.ic_placeholder_light_medium)
        .into(albumArt);
}


    @Override
public void onColorSelection(@NonNull ColorChooserDialog dialog, @ColorInt int selectedColor) {
    if (dialog == textColorDialog) {
        textColor = selectedColor;
        prefs.edit().putInt(BaseWidgetProvider.ARG_WIDGET_TEXT_COLOR + appWidgetId, selectedColor).apply();
        applyTextColorToWidget();
    } else if (dialog == backgroundColorDialog) {
        backgroundColor = selectedColor;
        prefs.edit().putInt(BaseWidgetProvider.ARG_WIDGET_BACKGROUND_COLOR + appWidgetId, selectedColor).apply();
        applyBackgroundColorToWidget();
    }
}

private void applyTextColorToWidget() {
    Fragment fragment = adapter.getRegisteredFragment(pager.getCurrentItem());
    if (fragment == null) return;

    View view = fragment.getView();
    if (view == null) return;

    int[] textIds = {R.id.text1, R.id.text2, R.id.text3};
    for (int id : textIds) {
        TextView textView = view.findViewById(id);
        if (textView != null) {
            textView.setTextColor(textColor);
        }
    }
}

private void applyBackgroundColorToWidget() {
    Fragment fragment = adapter.getRegisteredFragment(pager.getCurrentItem());
    if (fragment == null) return;

    View view = fragment.getView();
    if (view == null) return;

    View layout = view.findViewById(getRootViewId());
    if (layout != null) {
        layout.setBackgroundColor(ColorUtils.adjustAlpha(backgroundColor, alpha));
    }
}


    @Override
    public void onColorChooserDismissed(@NonNull ColorChooserDialog dialog) {

    }

    @Nullable
    @Override
    public String key() {
        return "widget_activity";
    }
}