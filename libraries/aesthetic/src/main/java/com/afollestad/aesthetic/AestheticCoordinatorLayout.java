package com.afollestad.aesthetic;

import static com.afollestad.aesthetic.Rx.onErrorLogAndRethrow;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.util.Pair;
import android.support.v7.view.menu.ActionMenuItemView;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.View;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import java.lang.reflect.Field;

/** @author Aidan Follestad (afollestad) */
public class AestheticCoordinatorLayout extends CoordinatorLayout
    implements AppBarLayout.OnOffsetChangedListener {

  private Disposable toolbarColorSubscription;
  private Disposable statusBarColorSubscription;
  private AppBarLayout appBarLayout;
  private View colorView;
  private AestheticToolbar toolbar;
  private CollapsingToolbarLayout collapsingToolbarLayout;

  private int toolbarColor;
  private ActiveInactiveColors iconTextColors;
  private int lastOffset = -1;

  public AestheticCoordinatorLayout(Context context) {
    super(context);
  }

  public AestheticCoordinatorLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AestheticCoordinatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @SuppressWarnings("unchecked")
  private static void tintMenu(
      @NonNull AestheticToolbar toolbar, @Nullable Menu menu, final ActiveInactiveColors colors) {

    setNavigationAndOverflow(toolbar, colors);
    applyCollapseIconTint(toolbar, colors);
    tintActionMenuItems(toolbar, colors);

    if (menu == null) {
      menu = toolbar.getMenu();
    }
    ViewUtil.tintToolbarMenu(toolbar, menu, colors);
  }

  private static void setNavigationAndOverflow(@NonNull AestheticToolbar toolbar, ActiveInactiveColors colors) {
    if (toolbar.getNavigationIcon() != null) {
      toolbar.setNavigationIcon(toolbar.getNavigationIcon(), colors.activeColor());
    }
    Util.setOverflowButtonColor(toolbar, colors.activeColor());
  }

  private static void applyCollapseIconTint(@NonNull AestheticToolbar toolbar, ActiveInactiveColors colors) {
    try {
      final Field field = Toolbar.class.getDeclaredField("mCollapseIcon");
      Drawable collapseIcon = (Drawable) field.get(toolbar);
      if (collapseIcon != null) {
        toolbar.setNavigationIcon(TintHelper.createTintedDrawable(collapseIcon, colors.toEnabledSl()));
      }
    } catch (Exception e) {
      Log.w("Aesthetic", "Failed to tint collapse icon", e);
    }
  }

  private static void tintActionMenuItems(@NonNull AestheticToolbar toolbar, ActiveInactiveColors colors) {
    final PorterDuffColorFilter colorFilter =
        new PorterDuffColorFilter(colors.activeColor(), PorterDuff.Mode.SRC_IN);

    for (int i = 0; i < toolbar.getChildCount(); i++) {
      View child = toolbar.getChildAt(i);
      if (child instanceof ActionMenuView) {
        tintActionMenuView((ActionMenuView) child, colorFilter);
      }
    }
  }

  private static void tintActionMenuView(ActionMenuView menuView, PorterDuffColorFilter colorFilter) {
    for (int j = 0; j < menuView.getChildCount(); j++) {
      View itemView = menuView.getChildAt(j);
      if (itemView instanceof ActionMenuItemView) {
        Drawable[] drawables = ((ActionMenuItemView) itemView).getCompoundDrawables();
        for (Drawable drawable : drawables) {
          if (drawable != null) {
            drawable.setColorFilter(colorFilter);
          }
        }
      }
    }
  }

  @Override
  public void onAttachedToWindow() {
      super.onAttachedToWindow();

      findToolbarAndColorView();

      if (toolbar != null && colorView != null) {
          subscribeToToolbarColorUpdates();
      }

      if (collapsingToolbarLayout != null) {
          subscribeToStatusBarColorUpdates();
      }
  }

  private void findToolbarAndColorView() {
      if (getChildCount() == 0) return;

      View firstChild = getChildAt(0);
      if (!(firstChild instanceof AppBarLayout)) return;

      appBarLayout = (AppBarLayout) firstChild;
      if (appBarLayout.getChildCount() == 0) return;

      View collapsingChild = appBarLayout.getChildAt(0);
      if (!(collapsingChild instanceof CollapsingToolbarLayout)) return;

      collapsingToolbarLayout = (CollapsingToolbarLayout) collapsingChild;

      for (int i = 0; i < collapsingToolbarLayout.getChildCount(); i++) {
          View child = collapsingToolbarLayout.getChildAt(i);

          if (toolbar == null && child instanceof AestheticToolbar) {
              toolbar = (AestheticToolbar) child;
          } else if (colorView == null && child.getBackground() instanceof ColorDrawable) {
              colorView = child;
          }

          if (toolbar != null && colorView != null) break;
      }
  }

  private void subscribeToToolbarColorUpdates() {
      appBarLayout.addOnOffsetChangedListener(this);

      toolbarColorSubscription = Observable.combineLatest(
              toolbar.colorUpdated(),
              Aesthetic.get(getContext()).colorIconTitle(toolbar.colorUpdated()),
              (integer, activeInactiveColors) -> Pair.create(integer, activeInactiveColors))
          .compose(Rx.distinctToMainThread())
          .subscribe(
              result -> {
                  toolbarColor = result.first;
                  iconTextColors = result.second;
                  invalidateColors();
              },
              onErrorLogAndRethrow()
          );
  }

  private void subscribeToStatusBarColorUpdates() {
      statusBarColorSubscription = Aesthetic.get(getContext())
          .colorStatusBar()
          .compose(Rx.distinctToMainThread())
          .subscribe(
              color -> {
                  collapsingToolbarLayout.setContentScrimColor(color);
                  collapsingToolbarLayout.setStatusBarScrimColor(color);
              },
              onErrorLogAndRethrow()
          );
  }


  @Override
  public void onDetachedFromWindow() {
    if (toolbarColorSubscription != null) {
      toolbarColorSubscription.dispose();
    }
    if (statusBarColorSubscription != null) {
      statusBarColorSubscription.dispose();
    }
    if (this.appBarLayout != null) {
      this.appBarLayout.removeOnOffsetChangedListener(this);
      this.appBarLayout = null;
    }
    this.toolbar = null;
    this.colorView = null;
    super.onDetachedFromWindow();
  }

  @Override
  public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
    if (lastOffset == Math.abs(verticalOffset)) {
      return;
    }
    lastOffset = Math.abs(verticalOffset);
    invalidateColors();
  }

  private void invalidateColors() {
    if (iconTextColors == null) {
      return;
    }

    final int maxOffset = appBarLayout.getMeasuredHeight() - toolbar.getMeasuredHeight();
    final float ratio = (float) lastOffset / (float) maxOffset;

    final int colorViewColor = ((ColorDrawable) colorView.getBackground()).getColor();
    final int blendedColor = Util.blendColors(colorViewColor, toolbarColor, ratio);
    final int collapsedTitleColor = iconTextColors.activeColor();
    final int expandedTitleColor = Util.isColorLight(colorViewColor) ? Color.BLACK : Color.WHITE;
    final int blendedTitleColor = Util.blendColors(expandedTitleColor, collapsedTitleColor, ratio);

    toolbar.setBackgroundColor(blendedColor);

    collapsingToolbarLayout.setCollapsedTitleTextColor(collapsedTitleColor);
    collapsingToolbarLayout.setExpandedTitleColor(expandedTitleColor);

    tintMenu(
        toolbar,
        toolbar.getMenu(),
        ActiveInactiveColors.create(blendedTitleColor, Util.adjustAlpha(blendedColor, 0.7f)));
  }
}
