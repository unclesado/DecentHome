/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import com.dev.sadooo.decenthome.R;

/*
 * Ths bar will manage the transition between the QSB search bar and the delete drop
 * targets so that each of the individual IconDropTargets don't have to.
 */
public class SearchDropTargetBar extends FrameLayout implements DragController.DragListener {

    private static final int sTransitionInDuration = 200;
    private static final int sTransitionOutDuration = 175;

    private ObjectAnimator mDropTargetBarAnim;
    private AnimatorSet mQSBSearchBarAnim;
    private static final AccelerateInterpolator sAccelerateInterpolator =
            new AccelerateInterpolator();

    private boolean mIsSearchBarHidden;
    private View mQSBSearchBar;
    private View mDropTargetBar;
    private ButtonDropTarget mInfoDropTarget;
    private ButtonDropTarget mDeleteDropTarget;
    private ButtonDropTarget mMoveHomeDragTarget;
    private int mBarHeight;
    private int mWorkspaceHeight;
    private boolean mDeferOnDragEnd = false;

    private boolean mEnableDropDownDropTargets;

    private Launcher mLauncher;
	private Context mContext;

    public SearchDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		mContext = context;
        // Ensure a minimal height while view is finishing to inflate
        final Resources res = context.getResources();
        mBarHeight = res.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_height);
    }

    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.addDragListener(mInfoDropTarget);
        dragController.addDragListener(mDeleteDropTarget);
        dragController.addDragListener(mMoveHomeDragTarget);
        dragController.addDropTarget(mInfoDropTarget);
        dragController.addDropTarget(mDeleteDropTarget);
        dragController.addDropTarget(mMoveHomeDragTarget);
        dragController.setFlingToDeleteDropTarget(mDeleteDropTarget);
        mInfoDropTarget.setLauncher(launcher);
        mDeleteDropTarget.setLauncher(launcher);
		mMoveHomeDragTarget.setLauncher(launcher);
        setupQSB(launcher);
    }

    public void setupQSB(Launcher launcher) {
        mLauncher = launcher;
        mQSBSearchBar = launcher.getQsbBar();
    }

    public void setQsbSearchBar(View qsb) {
        float alpha = 1f;
        int visibility = View.VISIBLE;
        if (mQSBSearchBar != null) {
            alpha = mQSBSearchBar.getAlpha();
            visibility = mQSBSearchBar.getVisibility();
        }
        mQSBSearchBar = qsb;
        mQSBSearchBarAnim = new AnimatorSet();
        if (mQSBSearchBar != null) {
            mQSBSearchBar.setAlpha(alpha);
            mQSBSearchBar.setVisibility(visibility);
            View animView = mQSBSearchBar;
            if (!mLauncher.isSearchBarEnabled() && mLauncher.mGrid.shouldAnimQSBWithWorkspace()) {
                final ViewGroup view = mLauncher.getWorkspace();
                animView = view;

                ValueAnimator anim1 = LauncherAnimUtils.ofFloat(
                        animView, "translationY", 0, mBarHeight / 2);
                ValueAnimator  anim2 = ValueAnimator.ofInt(0, mBarHeight);
                anim2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        final int val = (int) animation.getAnimatedValue();
                        final int height = mWorkspaceHeight - val;
                        final ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                        if (val == 0) {
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        } else {
                            layoutParams.height = height;
                        }
                        view.setLayoutParams(layoutParams);
                    }
                });
                mQSBSearchBarAnim.playTogether(anim1, anim2);
            } else if (mEnableDropDownDropTargets) {
                mQSBSearchBarAnim.play(LauncherAnimUtils.ofFloat(
                        animView, "translationY", 0, -mBarHeight));
            } else {
                mQSBSearchBarAnim.play(LauncherAnimUtils.ofFloat(animView, "alpha", 1f, 0f));
            }
            setupAnimation(mQSBSearchBarAnim, animView);
        } else {
            // Create a no-op animation of the search bar is null
            mQSBSearchBarAnim.play(ValueAnimator.ofFloat(0, 0));
            mQSBSearchBarAnim.setDuration(sTransitionInDuration);
        }
    }

    private void prepareStartAnimation(View v) {
        // Enable the hw layers before the animation starts (will be disabled in the onAnimationEnd
        // callback below)
        if (v != null) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    private void setupAnimation(Animator anim, final View v) {
        anim.setInterpolator(sAccelerateInterpolator);
        anim.setDuration(sTransitionInDuration);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (v != null) {
                    v.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mDropTargetBar = findViewById(R.id.drag_target_bar);
        mInfoDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.info_target_text);
        mDeleteDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.delete_target_text);
        mMoveHomeDragTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.move_home_target_text);

        mInfoDropTarget.setSearchDropTargetBar(this);
        mDeleteDropTarget.setSearchDropTargetBar(this);
        mMoveHomeDragTarget.setSearchDropTargetBar(this);

        mEnableDropDownDropTargets =
            getResources().getBoolean(R.bool.config_useDropTargetDownTransition);

        // Create the various fade animations
        if (mEnableDropDownDropTargets) {
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            mBarHeight = grid.searchBarSpaceHeightPx;
            mDropTargetBar.setTranslationY(-mBarHeight);
            mDropTargetBarAnim = LauncherAnimUtils.ofFloat(mDropTargetBar, "translationY",
                    -mBarHeight, 0f);

        } else {
            mDropTargetBar.setAlpha(0f);
            mDropTargetBarAnim = LauncherAnimUtils.ofFloat(mDropTargetBar, "alpha", 0f, 1f);
        }
        setupAnimation(mDropTargetBarAnim, mDropTargetBar);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mLauncher.getWorkspace() != null) {
            mWorkspaceHeight = ((ViewGroup)mLauncher.getWorkspace().getParent()).getHeight();
        }
    }

    public void finishAnimations() {
        prepareStartAnimation(mDropTargetBar);
        mDropTargetBarAnim.reverse();
        prepareStartAnimation(mQSBSearchBar);
        mQSBSearchBarAnim.cancel();
    }

    /*
     * Shows and hides the search bar.
     */
    public void showSearchBar(boolean animated) {
        boolean needToCancelOngoingAnimation = mQSBSearchBarAnim.isRunning() && !animated;
        if ((!mIsSearchBarHidden && !needToCancelOngoingAnimation) ||
                (!mLauncher.isSearchBarEnabled() && mLauncher.mGrid.shouldAnimQSBWithWorkspace())) {
            return;
        }
        if (animated) {
            prepareStartAnimation(mQSBSearchBar);
            mQSBSearchBarAnim.cancel();
        } else {
            mQSBSearchBarAnim.cancel();
            if (mQSBSearchBar != null && mEnableDropDownDropTargets) {
                mQSBSearchBar.setTranslationY(0);
            } else if (mQSBSearchBar != null) {
                mQSBSearchBar.setAlpha(1f);
            }
        }
        mIsSearchBarHidden = false;
    }
    public void hideSearchBar(boolean animated) {
        boolean needToCancelOngoingAnimation = mQSBSearchBarAnim.isRunning() && !animated;
        if ((mIsSearchBarHidden && !needToCancelOngoingAnimation) ||
                (!mLauncher.isSearchBarEnabled() && mLauncher.mGrid.shouldAnimQSBWithWorkspace())) {
            return;
        }
        if (animated) {
            prepareStartAnimation(mQSBSearchBar);
            mQSBSearchBarAnim.start();
        } else {
            mQSBSearchBarAnim.cancel();
            if (mQSBSearchBar != null && mEnableDropDownDropTargets) {
                mQSBSearchBar.setTranslationY(-mBarHeight);
            } else if (mQSBSearchBar != null) {
                mQSBSearchBar.setAlpha(0f);
            }
        }
        mIsSearchBarHidden = true;
    }

    /*
     * Gets various transition durations.
     */
    public int getTransitionInDuration() {
        return sTransitionInDuration;
    }
    public int getTransitionOutDuration() {
        return sTransitionOutDuration;
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        // Animate out the QSB search bar, and animate in the drop target bar
        prepareStartAnimation(mDropTargetBar);
        hideStatusBar();
        mDropTargetBarAnim.start();
        if (!isAnyFolderOpen() && (!mIsSearchBarHidden ||
                (mQSBSearchBar != null && mQSBSearchBar.getAlpha() > 0f))) {
            prepareStartAnimation(mQSBSearchBar);
            mQSBSearchBarAnim.start();
        }
    }

    public void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    private boolean isAnyFolderOpen() {
        if (mLauncher != null) {
            return mLauncher.getWorkspace().getOpenFolder() != null;
        }
        return false;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            // Restore the QSB search bar, and animate out the drop target bar
            prepareStartAnimation(mDropTargetBar);
            mDropTargetBarAnim.reverse();
			showStatusBar();
            if (!isAnyFolderOpen() && (!mIsSearchBarHidden ||
                    (mQSBSearchBar != null && mQSBSearchBar.getAlpha() < 1f))) {
                if (mLauncher != null && mQSBSearchBar != null && mLauncher.shouldShowSearchBar()
                        && mQSBSearchBar.getVisibility() != View.VISIBLE) {
                    mQSBSearchBar.setVisibility(View.VISIBLE);
                }
                prepareStartAnimation(mQSBSearchBar);
                mQSBSearchBarAnim.cancel();
            }
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public Rect getSearchBarBounds() {
        if (mQSBSearchBar != null) {
            final int[] pos = new int[2];
            mQSBSearchBar.getLocationOnScreen(pos);

            final Rect rect = new Rect();
            rect.left = pos[0];
            rect.top = pos[1];
            rect.right = pos[0] + mQSBSearchBar.getWidth();
            rect.bottom = pos[1] + mQSBSearchBar.getHeight();
            return rect;
        } else {
            return null;
        }
    }

    public View getDropTargetBar() {
        return mDropTargetBar;
    }

	private void hideStatusBar(){
        if (Build.VERSION.SDK_INT < 16) {
            ((Activity)mContext).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else if(Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 19) {
            View decorView = ((Activity)mContext).getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            ActionBar actionBar = ((Activity)mContext).getActionBar();
            if(actionBar != null) {
                actionBar.hide();
            }
        } else if(Build.VERSION.SDK_INT >= 19){
            View decorView = ((Activity)mContext).getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        }
    }
    private void showStatusBar(){
        if (Build.VERSION.SDK_INT < 16) {
            ((Activity)mContext).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else if(Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 19){
            View decorView = ((Activity)mContext).getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
            decorView.setSystemUiVisibility(uiOptions);
            ActionBar actionBar = ((Activity)mContext).getActionBar();
            if(actionBar != null) {
                actionBar.show();
            }
        } else if(Build.VERSION.SDK_INT >= 19){
            View decorView = ((Activity)mContext).getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        }
    }
}
