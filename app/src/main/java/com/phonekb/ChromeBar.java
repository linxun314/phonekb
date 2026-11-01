package com.phonekb;

import android.content.Context;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A single-row toolbar that gracefully degrades on narrow screens.
 *
 * <p>Children are either {@link #addFixed fixed} (power button, status title, page tabs) or
 * {@link #addCollapsible collapsible} (secondary actions such as 单手/右手/互换/旋转/伴侣/帮助).
 * During measurement the bar first tries to show every child; when the row does not fit it
 * hides collapsible children from the right end and shows a single 「更多」 button that exposes
 * the hidden actions in a popup menu. Because the decision is pure measurement, the same code
 * works for any phone width, font size, split-screen or foldable state without hard-coded
 * breakpoints.
 */
public class ChromeBar extends LinearLayout {

    /** Late-bound label so popup entries follow state such as 右手/左手 or 帮助/配对. */
    public interface Labeler { String label(); }

    private static final class Item {
        final View view;
        final Labeler labeler;
        final Runnable action;

        Item(View view, Labeler labeler, Runnable action) {
            this.view = view;
            this.labeler = labeler;
            this.action = action;
        }
    }

    private final List<Item> collapsible = new ArrayList<>();
    /** Collapsible views this bar itself set to GONE; restored at the start of each measure. */
    private final List<View> overflowHidden = new ArrayList<>();
    private TextView moreButton;
    private int minTitleWidthPx = Ui.dpi(getContext(), 72);
    private int hiddenCount;

    public ChromeBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    /** Minimum width reserved for the weight=1 child (the device-name column). */
    public void setMinTitleWidthDp(float dp) {
        minTitleWidthPx = Ui.dpi(getContext(), dp);
        requestLayout();
    }

    public void addFixed(View view, ViewGroup.LayoutParams lp) {
        addView(view, lp);
    }

    /** A child that may be folded into the 「更多」 menu when the row is too narrow. */
    public void addCollapsible(View view, ViewGroup.LayoutParams lp, String label, Runnable action) {
        addCollapsible(view, lp, () -> label, action);
    }

    public void addCollapsible(View view, ViewGroup.LayoutParams lp, Labeler labeler, Runnable action) {
        collapsible.add(new Item(view, labeler, action));
        addView(view, lp);
    }

    private TextView ensureMoreButton() {
        if (moreButton != null) return moreButton;
        moreButton = new TextView(getContext());
        moreButton.setText("更多");
        moreButton.setTextColor(Ui.TEXT);
        moreButton.setTextSize(13);
        moreButton.setGravity(Gravity.CENTER);
        moreButton.setSingleLine(true);
        moreButton.setBackground(Ui.rounded(getContext(), Ui.SURFACE_2, 12));
        Ui.autoSize(moreButton, 13, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dpi(getContext(), 60), Ui.dpi(getContext(), 34));
        lp.setMargins(Ui.dpi(getContext(), 2), 0, Ui.dpi(getContext(), 2), 0);
        moreButton.setLayoutParams(lp);
        moreButton.setVisibility(GONE);
        moreButton.setOnClickListener(v -> showMoreMenu());
        addView(moreButton, lp);
        return moreButton;
    }

    private PopupMenu overflowMenu;

    private void showMoreMenu() {
        if (collapsible.isEmpty()) return;
        dismissOverflow();
        PopupMenu menu = new PopupMenu(getContext(), moreButton);
        overflowMenu = menu;
        menu.setOnDismissListener(m -> {
            if (overflowMenu == m) overflowMenu = null;
        });
        boolean any = false;
        for (int i = 0; i < collapsible.size(); i++) {
            Item item = collapsible.get(i);
            if (item.view.getVisibility() == VISIBLE) continue;
            if (!overflowHidden.contains(item.view)) continue;
            String label = item.labeler == null ? "" : item.labeler.label();
            menu.getMenu().add(Menu.NONE, i, i, label);
            any = true;
        }
        if (!any) return;
        menu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index >= 0 && index < collapsible.size()) {
                Runnable action = collapsible.get(index).action;
                if (action != null) action.run();
            }
            return true;
        });
        menu.show();
    }

    /** Number of actions currently folded into the 「更多」 menu (0 when everything fits). */
    public int hiddenCount() { return hiddenCount; }

    /** Closes the overflow menu if it is currently showing (called when the activity pauses). */
    public void dismissOverflow() {
        if (overflowMenu == null) return;
        PopupMenu menu = overflowMenu;
        overflowMenu = null;
        try {
            menu.dismiss();
        } catch (RuntimeException ignored) {
        }
    }

    /** Test hook: open the overflow menu from adb so its contents can be screenshotted. */
    public void showMoreMenuForDebug() {
        post(this::showMoreMenu);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        ensureMoreButton();

        // Undo our own overflow hiding; views the app keeps GONE (e.g. 互换 pill on other
        // pages) must stay excluded and must not appear in the 更多 menu.
        for (View view : overflowHidden) {
            if (view.getVisibility() != VISIBLE) view.setVisibility(VISIBLE);
        }
        overflowHidden.clear();

        if (widthMode == MeasureSpec.UNSPECIFIED || collapsible.isEmpty()) {
            hiddenCount = 0;
            moreButton.setVisibility(GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int available = width - getPaddingLeft() - getPaddingRight();
        int base = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == moreButton || isCollapsibleChild(child)) continue;
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp != null && lp.weight > 0) {
                base += minTitleWidthPx + lp.leftMargin + lp.rightMargin;
            } else {
                measureChildForOverflow(child, width);
                base += child.getMeasuredWidth() + margins(lp);
            }
        }

        List<View> visibleItems = new ArrayList<>();
        int[] widths = new int[collapsible.size()];
        int allWidth = 0;
        for (int i = 0; i < collapsible.size(); i++) {
            View child = collapsible.get(i).view;
            if (child.getVisibility() == GONE) {
                widths[i] = 0;
                continue;
            }
            visibleItems.add(child);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp != null && lp.width > 0) {
                widths[i] = lp.width + margins(lp);
            } else {
                measureChildForOverflow(child, width);
                widths[i] = child.getMeasuredWidth() + margins(lp);
            }
            allWidth += widths[i];
        }

        int moreWidth = Ui.dpi(getContext(), 64);
        if (moreButton.getLayoutParams() instanceof LayoutParams) {
            moreWidth = ((LayoutParams) moreButton.getLayoutParams()).width + margins(
                    (LayoutParams) moreButton.getLayoutParams());
        }

        // Hide collapsible items from the right end, but reserve room for the 「更多」 button
        // as soon as one item is hidden. This keeps the status title at its minimum width
        // instead of letting LinearLayout squeeze it into a vertical stack of characters.
        int show = visibleItems.size();
        int visibleWidth = allWidth;
        while (show > 0) {
            boolean needMore = show < visibleItems.size();
            int needed = base + visibleWidth + (needMore ? moreWidth : 0);
            if (needed <= available) break;
            show--;
            View hidden = visibleItems.get(show);
            for (int i = collapsible.size() - 1; i >= 0; i--) {
                if (collapsible.get(i).view == hidden) {
                    visibleWidth -= widths[i];
                    break;
                }
            }
        }
        boolean withMore = show < visibleItems.size();

        for (int i = 0; i < visibleItems.size(); i++) {
            View child = visibleItems.get(i);
            if (i < show) continue;
            child.setVisibility(GONE);
            overflowHidden.add(child);
        }
        hiddenCount = visibleItems.size() - show;
        moreButton.setVisibility(hiddenCount > 0 ? VISIBLE : GONE);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private boolean isCollapsibleChild(View child) {
        for (Item item : collapsible) if (item.view == child) return true;
        return false;
    }

    private void measureChildForOverflow(View child, int parentWidth) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        int childWidth = lp != null && lp.width > 0
                ? MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                : MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.AT_MOST);
        int childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(childWidth, childHeight);
    }

    private static int margins(LayoutParams lp) {
        if (lp == null) return 0;
        return lp.leftMargin + lp.rightMargin;
    }
}
