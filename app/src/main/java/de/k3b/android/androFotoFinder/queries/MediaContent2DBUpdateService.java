/*
 * Copyright (c) 2019-2020 by k3b.
 *
 * This file is part of AndroFotoFinder / #APhotoManager.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>
 */
package de.k3b.android.androFotoFinder.queries;

import android.content.Context;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.widget.Toast;

import java.util.Date;

import de.k3b.io.IProgessListener;

/**
 * #155: takes care that chages from
 * {@link MediaContentproviderRepository} are transfered to {@link MediaDBRepository}
 */
public class MediaContent2DBUpdateService {
    // called when image-/file-mediacontent has changed to indicate that data must
    // be loaded from content-provider to content-copy
    private static final ContentObserver mMediaObserverDirectory = new ContentObserver(null) {

        // ignore version with 3rd param: int userId
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

        }
    };
    public static MediaContent2DBUpdateService instance = null;
    private final Context context;
    private final SQLiteDatabase writableDatabase;

    public MediaContent2DBUpdateService(Context context, SQLiteDatabase writableDatabase) {
        this.context = context;
        this.writableDatabase = writableDatabase;
    }

    public void clearMediaCopy() {
        DatabaseHelper.version2Upgrade_RecreateMediDbCopy(writableDatabase);
    }

    public void rebuild(Context context, IProgessListener progessListener) {
        long start = new Date().getTime();
        clearMediaCopy();
        MediaDBRepository.Impl.updateMedaiCopy(context, writableDatabase, null, progessListener);
        start = (new Date().getTime() - start) / 1000;
        final String text = "load db " + start + " secs";
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
        if (progessListener != null) progessListener.onProgress(0, 0, text);
    }


}
