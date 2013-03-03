/*
 * SnD_ListView is an extension of the default android ListView widget
 * adding a Swipe-And-Dismiss functionality.
 * Works on Android since 2.3
 * Copyright (C) 2013 Sergey Zubarev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * User is supposed to provide an object implementing a DismissListener
 * interface which is responsible to remove an item from the underling
 * data adapter. Typical implementation will look like:
 *
 *    class DismissListener implements SnD_ListView.DismissListener
 *    {
 *        private ArrayAdapter<ITEM_TYPE> m_adapter;
 *
 *        DismissListener( ArrayAdapter<ITEM_TYPE> adapter )
 *        {
 *            m_adapter = adapter;
 *        }
 *
 *        public void onDismiss( int pos )
 *        {
 *            ITEM_TYPE item = m_adapter.getItem( pos );
 *            if (item != null)
 *            {
 *                m_adapter.remove( item );
 *                m_adapter.notifyDataSetChanged();
 *            }
 *        }
 *    };
 *
 */

package com.znc;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.widget.ListView;


public class SnD_ListView extends ListView
{
    private static final String LOG_TAG = SnD_ListView.class.getName();

    public interface DismissListener
    {
        void onDismiss( int pos );
    };

    private class TouchListener implements View.OnTouchListener
    {
        private DismissListener m_dismissListener;
        private VelocityTracker m_velocityTracker;
        private View m_view;
        private float m_downX;
        private int m_slop;
        private int m_minFlingVelocity;
        private int m_maxFlingVelocity;

        private class SwipeAnimationListener implements Animation.AnimationListener
        {
            private DismissListener m_dismissListener;
            private View m_view;
            private int m_itemPos;

            public SwipeAnimationListener( DismissListener dismissListener, View view, int itemPos )
            {
                m_dismissListener = dismissListener;
                m_view = view;
                m_itemPos = itemPos;
            }

            public void onAnimationStart( Animation animation )
            {
            }

            public void onAnimationRepeat( Animation animation )
            {
            }

            public void onAnimationEnd( Animation animation )
            {
                m_dismissListener.onDismiss( m_itemPos );
                TranslateAnimation translateAnimation = new TranslateAnimation( 0, 0, 0, 0 );
                AlphaAnimation alphaAnimation = new AlphaAnimation( 1.0f, 1.0f );
                AnimationSet anim = new AnimationSet( true );
                anim.addAnimation( translateAnimation );
                anim.addAnimation( alphaAnimation );
                anim.setFillAfter( true );
                anim.setDuration( 0 );
                m_view.startAnimation( anim );
            }
        };

        private View get_child_view( ListView listView, MotionEvent motionEvent, int pos[] )
        {
            int location[] = new int[2];
            listView.getLocationOnScreen( location );
            int x = (int) motionEvent.getRawX() - location[0];
            int y = (int) motionEvent.getRawY() - location[1];

            int cnt = listView.getChildCount();
            Rect rect = new Rect();
            for (int idx=0; idx<cnt; idx++)
            {
                View child = listView.getChildAt( idx );
                child.getHitRect( rect );
                if (rect.contains(x, y))
                {
                    if (pos != null)
                    {
                        idx += listView.getFirstVisiblePosition();
                        pos[0] = idx;
                    }
                    return child;
                }
            }
            return null;
        }

        private float get_alpha( float absDeltaX, int viewWidth )
        {
            if (absDeltaX > viewWidth)
                absDeltaX = viewWidth;
            return 1.0f - (absDeltaX / viewWidth) * 0.8f;
        }

        private long get_duration( float absDistance )
        {
            return (long) (1000.0f * absDistance / 800.0f);
        }

        private float get_velocity()
        {
            m_velocityTracker.computeCurrentVelocity( 1000 );
            float velocityX = Math.abs( m_velocityTracker.getXVelocity() );
            float velocityY = Math.abs( m_velocityTracker.getYVelocity() );
            if ((m_minFlingVelocity <= velocityX) &&
                (velocityX <= m_maxFlingVelocity) &&
                (velocityY < velocityX))
            {
                return m_velocityTracker.getXVelocity();
            }
            return 0.0f;
        }

        public TouchListener( SnD_ListView listView )
        {
            m_velocityTracker = VelocityTracker.obtain();
            ViewConfiguration vc = ViewConfiguration.get(listView.getContext());
            m_slop = vc.getScaledTouchSlop();
            m_minFlingVelocity = vc.getScaledMinimumFlingVelocity();
            m_maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        }

        public void setDismissListener( DismissListener dismissListener )
        {
            m_dismissListener = dismissListener;
        }

        public boolean onTouch( View view, MotionEvent motionEvent )
        {
            int action = motionEvent.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN)
            {
                m_downX = motionEvent.getRawX();
                m_velocityTracker.clear();
                m_velocityTracker.addMovement( motionEvent );
                return true;
            }
            else if (action == MotionEvent.ACTION_MOVE)
            {
                m_velocityTracker.addMovement( motionEvent );
                float deltaX = (motionEvent.getRawX() - m_downX);
                float absDeltaX = Math.abs( deltaX );

                if (m_view == null)
                {
                    if (absDeltaX < m_slop)
                        return false;
                    m_view = get_child_view((ListView) view, motionEvent, null);
                    if (m_view == null)
                        return false;
                }

                int viewWidth = view.getWidth();
                float alpha = this.get_alpha( absDeltaX, viewWidth );

                TranslateAnimation translateAnimation = new TranslateAnimation( deltaX, deltaX, 0, 0 );
                AlphaAnimation alphaAnimation = new AlphaAnimation( alpha, alpha );
                AnimationSet anim = new AnimationSet( true );
                anim.addAnimation( translateAnimation );
                anim.addAnimation( alphaAnimation );
                anim.setFillAfter( true );
                anim.setDuration( 0 );
                m_view.startAnimation( anim );

                return true;
            }
            else if (action == MotionEvent.ACTION_UP)
            {
                if (m_view == null)
                    return false;

                m_velocityTracker.addMovement( motionEvent );

                int viewWidth = m_view.getWidth();
                float deltaX = (motionEvent.getRawX() - m_downX);
                float absDeltaX = Math.abs( deltaX );

                float fromAlpha = get_alpha( deltaX, viewWidth );
                AnimationSet anim = new AnimationSet( true );
                float velocity = get_velocity();
                float toAlpha;
                float toX;
                long duration;
                int pos[] = new int[1];

                if ((m_dismissListener != null) &&
                    ((absDeltaX > (viewWidth / 3)) || (velocity != 0.0f)) &&
                    (get_child_view((ListView) view, motionEvent, pos) == m_view))
                {
                    /* Dismiss view */
                    toAlpha = get_alpha( viewWidth, viewWidth );

                    if (absDeltaX > viewWidth)
                        duration = 0;
                    else
                        duration = get_duration( viewWidth - absDeltaX );

                    toX = viewWidth;
                    if (velocity != 0.0f)
                        toX *= Math.signum( velocity );
                    else
                        toX *= Math.signum( deltaX );

                    anim.setAnimationListener( new SwipeAnimationListener(m_dismissListener, m_view, pos[0]) );
                }
                else
                {
                    /* Move view back. */
                    toX = 0.0f;
                    toAlpha = 1.0f;
                    duration = get_duration( absDeltaX );
                }

                anim.addAnimation( new TranslateAnimation(deltaX, toX, 0, 0) );
                anim.addAnimation( new AlphaAnimation(fromAlpha, toAlpha) );
                anim.setDuration( duration );
                anim.setFillAfter( true );
                m_view.startAnimation( anim );
                m_view = null;
                return true;
            }
            return false;
        }
    };

    private TouchListener m_touchListener;

    public SnD_ListView( Context context )
    {
        super( context );
        m_touchListener = new TouchListener( this );
        this.setOnTouchListener( m_touchListener );
    }

    public SnD_ListView( Context context, AttributeSet attrSet )
    {
        super( context, attrSet );
        m_touchListener = new TouchListener( this );
        this.setOnTouchListener( m_touchListener );
    }

    public void setDismissListener( DismissListener dismissListener )
    {
        m_touchListener.setDismissListener( dismissListener );
    }
}
