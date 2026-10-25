package com.daimaobatiao.pet;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

// 八条本身：画当前帧，处理点、双击、长按和拖动
@SuppressLint("ViewConstructor")
final class PetView extends View {
    private final PetService svc;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final GestureDetector gd;
    private final int slop;

    private Bitmap bmp;
    private float bob, tilt, sx = 1, sy = 1;
    private boolean flip;

    private float downX, downY;
    private int startX, startY;
    private boolean dragging, longPressed;

    PetView(PetService svc) {
        super(svc);
        this.svc = svc;
        slop = ViewConfiguration.get(svc).getScaledTouchSlop();
        gd = new GestureDetector(svc, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { if (!dragging) svc.tap(); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { if (!dragging) svc.feed(); return true; }
            @Override public void onLongPress(MotionEvent e) {
                if (dragging) return;
                longPressed = true;
                svc.openSettings();
            }
        });
    }

    void set(Bitmap bmp, float bob, float tilt, float sx, float sy, boolean flip) {
        this.bmp = bmp; this.bob = bob; this.tilt = tilt; this.sx = sx; this.sy = sy; this.flip = flip;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        float sz = svc.sz();
        float k = Math.min(sz / bmp.getWidth(), sz / bmp.getHeight());
        // 以脚底中心为原点做颠、歪、翻转和伸缩
        m.reset();
        m.postTranslate(-bmp.getWidth() / 2f, -bmp.getHeight());
        m.postScale(k * sx * (flip ? -1 : 1), k * sy);
        m.postRotate((float) Math.toDegrees(-tilt));
        m.postTranslate(getWidth() / 2f, getHeight() - 2 * svc.d - bob);
        c.drawBitmap(bmp, m, paint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX(); downY = e.getRawY();
                startX = svc.winX(); startY = svc.winY();
                dragging = false; longPressed = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (!dragging && !longPressed && Math.abs(dx) + Math.abs(dy) > slop) {
                    dragging = true;
                    svc.beginDrag();
                }
                if (dragging) svc.dragTo(startX + dx, startY + dy);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) svc.endDrag();
                dragging = false;
                break;
        }
        return true;
    }
}
