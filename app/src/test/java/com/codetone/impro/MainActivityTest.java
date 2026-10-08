package com.codetone.impro;

import android.content.Context;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.DragEventBuilder;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private View root;

    @Before public void open() {
        RuntimeEnvironment.getApplication().getSharedPreferences("impro", Context.MODE_PRIVATE).edit().clear().commit();
        launch();
    }
    private void launch() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
        root = activity.getWindow().getDecorView();
    }
    @After public void close() { controller.pause().stop().destroy(); }

    @Test public void launchesWithFourBarsAndChordPreview() {
        assertNull("Touch UI must not show a text editor", first(root, EditText.class));
        assertEquals(4, first(root, GridLayout.class).getChildCount());
        assertNotNull(label(root, "Cmaj7"));
        assertNotNull(label(root, "▶  재생"));
        assertTrue(bar(0).isClickable());
        assertTrue(bar(0).isLongClickable());
    }

    @Test public void touchPickerChangesRootQualityAndSlashBass() {
        View dialog = openPicker(0);
        press(dialog, "B");
        press(dialog, "♭");
        press(dialog, "m7");
        press(dialog, "베이스 · 루트");
        AlertDialog bass = (AlertDialog) ShadowDialog.getLatestDialog();
        bass.getListView().performItemClick(null, 5, 5); // E bass
        press(dialog, "저장");
        assertEquals("Bbm7/E", barText(0));
        assertNotNull(label(root, "Bbm7/E"));
        assertEquals(4, first(root, GridLayout.class).getChildCount());
    }

    @Test public void cancellingPickerKeepsProgressionUnchanged() {
        View dialog = openPicker(1);
        press(dialog, "쉼");
        press(dialog, "취소");
        assertEquals(4, first(root, GridLayout.class).getChildCount());
        assertEquals("Am7", barText(1));
        assertFalse(button("↶ 되돌리기").isEnabled());
    }

    @Test public void addingBarIsCommittedOnlyOnConfirmation() {
        button("+ 마디").performClick();
        View dialog = dialogRoot();
        press(dialog, "취소");
        assertEquals(4, first(root, GridLayout.class).getChildCount());
        button("+ 마디").performClick();
        dialog = dialogRoot();
        press(dialog, "쉼");
        press(dialog, "마디 추가");
        assertEquals(5, first(root, GridLayout.class).getChildCount());
        assertEquals("NC", barText(4));
    }

    @Test public void multipleChordsShareBarAndRespectFourChordLimit() {
        View dialog = openPicker(0);
        press(dialog, "+ 코드");
        press(dialog, "G");
        press(dialog, "7");
        assertNotNull(label(dialog, "코드 2개 · 각각 2박씩 연주"));
        press(dialog, "+ 코드");
        press(dialog, "+ 코드");
        assertFalse(((Button) label(dialog, "+ 코드")).isEnabled());
        press(dialog, "코드 삭제");
        press(dialog, "코드 삭제");
        press(dialog, "저장");
        assertEquals("Cmaj7  G7", barText(0));
        assertEquals(2, editor().progression().bars.get(0).chords.size());
    }

    @Test public void duplicateDeleteAndUndoPreserveBarContents() {
        openPicker(1);
        press(dialogRoot(), "취소");
        button("복제").performClick();
        assertEquals(5, first(root, GridLayout.class).getChildCount());
        assertEquals("Am7", barText(2));
        button("삭제").performClick();
        assertEquals(4, first(root, GridLayout.class).getChildCount());
        button("↶ 되돌리기").performClick();
        assertEquals(5, first(root, GridLayout.class).getChildCount());
        assertEquals("Am7", barText(2));
        button("↶ 되돌리기").performClick();
        assertEquals(4, first(root, GridLayout.class).getChildCount());
    }

    @Test public void nativeDragEventsReorderForwardAndBackward() {
        drop(0, 2);
        assertEquals("Am7 | Dm7 | Cmaj7 | G7", editor().serialize());
        assertEquals("Cmaj7", barText(2));
        drop(3, 0);
        assertEquals("G7 | Am7 | Dm7 | Cmaj7", editor().serialize());
        button("↶ 되돌리기").performClick();
        assertEquals("Am7 | Dm7 | Cmaj7 | G7", editor().serialize());
    }

    @Test public void cancelledAndForeignDragsLeaveBarsAlone() {
        MainActivity.BarDrag drag = new MainActivity.BarDrag(editor(), editor().revision(), 0, bar(0));
        View target = bar(2);
        assertTrue(target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, drag)));
        // Android ignores the return value of ENTERED/EXITED events.
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENTERED, drag));
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, drag));
        assertEquals("Cmaj7 | Am7 | Dm7 | G7", editor().serialize());
        assertFalse(target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, "foreign")));
        assertFalse(button("↶ 되돌리기").isEnabled());
    }

    @Test public void presetAndMeterUpdatePreview() {
        button("블루스").performClick();
        assertEquals(12, first(root, GridLayout.class).getChildCount());
        assertNotNull(label(root, "C7"));
        button("3/4").performClick();
        assertNotNull(label(root, "01 / 12 마디   ·   3/4"));
    }

    @Test public void inputAndMeterSurviveActivityRecreation() {
        View dialog = openPicker(0);
        press(dialog, "F");
        press(dialog, "저장");
        button("3/4").performClick();
        controller.pause().stop().destroy();
        launch();
        assertEquals("Fmaj7", barText(0));
        assertEquals(4, first(root, GridLayout.class).getChildCount());
        assertNotNull(label(root, "01 / 04 마디   ·   3/4"));
    }

    @Test public void loadsVersionOneMultiChordProgressionWithoutTextInput() {
        controller.pause().stop().destroy();
        RuntimeEnvironment.getApplication().getSharedPreferences("impro", Context.MODE_PRIVATE).edit()
                .putString("progression", "CM7 G7/B | / | NC").commit();
        launch();
        assertEquals("CM7  G7/B", barText(0));
        assertEquals("G7/B", barText(1));
        assertEquals("NC", barText(2));
        assertNull(first(root, EditText.class));
        View dialog = openPicker(0);
        assertNotNull(label(dialog, "M7"));
        press(dialog, "저장");
        assertEquals("CM7  G7/B", barText(0));
    }

    @Test public void cannotDeleteLastBar() {
        controller.pause().stop().destroy();
        RuntimeEnvironment.getApplication().getSharedPreferences("impro", Context.MODE_PRIVATE).edit()
                .putString("progression", "C").commit();
        launch();
        assertFalse(button("삭제").isEnabled());
        assertEquals(1, first(root, GridLayout.class).getChildCount());
    }

    private View bar(int index) { return first(root, GridLayout.class).getChildAt(index); }
    private String barText(int index) { return ((TextView) ((ViewGroup) bar(index)).getChildAt(1)).getText().toString(); }
    private ProgressionEditor editor() { return ReflectionHelpers.getField(activity, "editor"); }
    private View openPicker(int index) { assertTrue(bar(index).performClick()); return dialogRoot(); }
    private View dialogRoot() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue(dialog instanceof ChordPickerDialog);
        assertTrue(dialog.isShowing());
        return dialog.getWindow().getDecorView();
    }
    private static void press(View root, String value) {
        TextView found = label(root, value);
        assertTrue("Missing touch button " + value, found instanceof Button);
        assertTrue(found.performClick());
    }
    private void drop(int from, int to) {
        MainActivity.BarDrag drag = new MainActivity.BarDrag(editor(), editor().revision(), from, bar(from));
        View target = bar(to);
        assertTrue(target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, drag)));
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENTERED, drag));
        assertTrue(target.dispatchDragEvent(event(DragEvent.ACTION_DROP, drag)));
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, drag));
    }
    private static DragEvent event(int action, Object localState) {
        return DragEventBuilder.newBuilder().setAction(action).setLocalState(localState).setX(20).setY(20).build();
    }

    private Button button(String value) {
        TextView found = label(root, value);
        assertTrue("Missing button " + value, found instanceof Button);
        return (Button) found;
    }
    private static TextView label(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(value)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = label(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static <T extends View> T first(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = first(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
