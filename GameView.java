package com.mini.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {
    public interface L {
        void sel(Game.Node n);
        void moved(Game.Node n);
    }

    public L l;
    Game g;
    boolean edit, wasActive = true;
    long last;
    Game.Node drag;
    float lx, ly, screenX, screenY, dens = 1f;
    int rh = -1;

    public GameView(Context c, Game g, boolean edit) {
        super(c);
        this.g = g;
        this.edit = edit;
        g.edit = edit;
        dens = c.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        if (!wasActive) last = 0;
        float dt = last == 0 ? 0f : (now - last) / 1e9f;
        last = now;
        if (dt > 0.1f) dt = 0.1f;
        g.update(dt);
        g.draw(c, getWidth(), getHeight());
        if (g.layer != null) g.layer.sync(g);
        wasActive = g.active();
        if (wasActive) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (!edit) {
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                int i = e.getActionIndex();
                g.down(e.getPointerId(i), g.toVx(e.getX(i)), g.toVy(e.getY(i)));
            } else if (a == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    g.move(e.getPointerId(i), g.toVx(e.getX(i)), g.toVy(e.getY(i)));
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) {
                g.up(e.getPointerId(e.getActionIndex()));
            } else if (a == MotionEvent.ACTION_CANCEL) {
                g.upAll();
            }
            invalidate();
            return true;
        }
        float vx = g.toVx(e.getX()), vy = g.toVy(e.getY());
        if (a == MotionEvent.ACTION_DOWN) {
            rh = -1;
            screenX = e.getX();
            screenY = e.getY();
            if (g.editMode == Game.EDIT_SIZE && g.sel != null && g.sel.inOrder) {
                rh = g.handleAt(g.sel, vx, vy, 26f * dens / Math.max(0.1f, g.sx));
            }
            if (rh >= 0) {
                drag = g.sel;
                lx = vx;
                ly = vy;
                return true;
            }
            drag = g.hit(vx, vy, false);
            lx = vx;
            ly = vy;
            if (l != null) l.sel(drag);
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (drag != null) {
                if (rh >= 0) {
                    g.resizeBy(drag, rh, vx - lx, vy - ly);
                } else {
                    g.dragEdit(drag, lx, ly, vx, vy);
                }
                lx = vx;
                ly = vy;
            } else {
                g.editorPanX += e.getX() - screenX;
                g.editorPanY += e.getY() - screenY;
                screenX = e.getX();
                screenY = e.getY();
            }
            invalidate();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (drag != null && l != null) l.moved(drag);
            drag = null;
            rh = -1;
        }
        return true;
    }
}
