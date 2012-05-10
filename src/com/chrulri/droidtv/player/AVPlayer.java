/******************************************************************************
 *  DroidTV, live TV on Android devices with host USB port and a DVB tuner    *
 *  Copyright (C) 2012  Christian Ulrich <chrulri@gmail.com>                  *
 *                                                                            *
 *  This program is free software: you can redistribute it and/or modify      *
 *  it under the terms of the GNU General Public License as published by      *
 *  the Free Software Foundation, either version 3 of the License, or         *
 *  (at your option) any later version.                                       *
 *                                                                            *
 *  This program is distributed in the hope that it will be useful,           *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of            *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             *
 *  GNU General Public License for more details.                              *
 *                                                                            *
 *  You should have received a copy of the GNU General Public License         *
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package com.chrulri.droidtv.player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceHolder;

public final class AVPlayer {

	public static final int STATE_UNKNOWN = 0;
	public static final int STATE_UNINITIALIZED = 1;
	public static final int STATE_PREPARING = 2;
	public static final int STATE_PREPARED = 3;
	public static final int STATE_PLAYING = 4;

	static {
		System.loadLibrary("avplayer");
	}

	private int mNativeContext = 0;
	
	private Bitmap mBitmap;
	private Matrix mMatrix = new Matrix();
	private RectF mBounds = new RectF();
	private RectF mSource = new RectF();

	private SurfaceHolder mSurface;
	private OnUpdateListener mListener;
	private String mFileName;

	private AVPlayer(SurfaceHolder surface, OnUpdateListener listener) {
		mSurface = surface;
		mListener = listener;

		mSurface.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				// ignore
			}

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				performRender();
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				mBounds.set(0, 0, width, height);
				mMatrix.setRectToRect(mSource, mBounds, ScaleToFit.CENTER);
				performRender();
			}
		});
		
		_initialize(new WeakReference<AVPlayer>(this));
	}
	
	private native void _initialize(WeakReference<AVPlayer> player);
	
	private native int _prepare(String fileName);

	private native int _start();

	private native int _stop();

	private native int _getState();

	private int postAudio(short[] buffer, int bufsize) {
		// TODO implement
		return 0;
	}

	private Bitmap postPrepare(int width, int height) {
		mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		return mBitmap;
	}

	private void postVideo() {
		performRender();
	}

	private void postNotify(int msg, int ext1, int ext2) {
		// TODO implement
	}

	private void performRender() {
		if (mBitmap != null) {
			Canvas c = mSurface.lockCanvas();
			if (c != null) {
				c.drawPaint(new Paint());
				c.drawBitmap(mBitmap, mMatrix, null);
				mSurface.unlockCanvasAndPost(c);
			}
		}
	}

	public void setDataSource(String fileName) {
		mFileName = fileName;
	}

	public void prepare() throws IOException {
		if (mFileName == null || !new File(mFileName).canRead()) {
			throw new FileNotFoundException();
		}
		int ret = _prepare(mFileName);
		if (ret != 0) {
			throw new IOException("prepare[" + ret + "]");
		}
	}

	public interface OnUpdateListener {
		// TODO implement
	}
}