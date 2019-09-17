/*
 * Copyright (c) 2015-2019 by k3b.
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

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.k3b.LibGlobal;
import de.k3b.android.androFotoFinder.Global;
import de.k3b.android.androFotoFinder.R;
import de.k3b.android.util.DBUtils;
import de.k3b.database.QueryParameter;
import de.k3b.io.AlbumFile;
import de.k3b.io.DirectoryFormatter;
import de.k3b.io.FileCommands;
import de.k3b.io.GalleryFilterParameter;
import de.k3b.io.GeoRectangle;
import de.k3b.io.IGalleryFilter;
import de.k3b.io.IGeoRectangle;
import de.k3b.io.ListUtils;
import de.k3b.io.StringUtils;
import de.k3b.io.VISIBILITY;
import de.k3b.io.collections.SelectedFiles;
import de.k3b.io.collections.SelectedItems;

/**
 * contains all SQL needed to query the android gallery
 *
 * Created by k3b on 04.06.2015.
 */
public class FotoSql extends FotoSqlBase {

    public static final int SORT_BY_DATE_OLD = 1;
    public static final int SORT_BY_NAME_OLD = 2;
    public static final int SORT_BY_LOCATION_OLD = 3;
    public static final int SORT_BY_NAME_LEN_OLD = 4;

    public static final int SORT_BY_DATE = 'd';
    public static final int SORT_BY_NAME = 'n';
    public static final int SORT_BY_LOCATION = 'l';
    public static final int SORT_BY_NAME_LEN = 's'; // size

    public static final int SORT_BY_FILE_LEN = 'f'; // file-size
    public static final int SORT_BY_WIDTH = 'w'; // width of image

    public static final int SORT_BY_RATING = 'r';
    public static final int SORT_BY_MODIFICATION = 'm';

    public static final int SORT_BY_DEFAULT = SORT_BY_DATE;

    public static final int QUERY_TYPE_UNDEFINED = 0;
    public static final int QUERY_TYPE_GALLERY = 11;
    public static final int QUERY_TYPE_GROUP_DATE = 12;
    public static final int QUERY_TYPE_GROUP_ALBUM = 13;
    public static final int QUERY_TYPE_GROUP_PLACE = 14;
    public static final int QUERY_TYPE_GROUP_PLACE_MAP = 141;

    public static final int QUERY_TYPE_GROUP_DATE_MODIFIED = 16;


    public static final int QUERY_TYPE_GROUP_COPY = 20;
    public static final int QUERY_TYPE_GROUP_MOVE = 21;
    public static final int QUERY_TYPE_TAG = 60;

    public static final int QUERY_TYPE_GROUP_DEFAULT = QUERY_TYPE_GROUP_ALBUM;
    public static final int QUERY_TYPE_DEFAULT = QUERY_TYPE_GALLERY;

    // columns that must be avaulable in the Cursor
    public static final String SQL_COL_PK = MediaStore.Images.Media._ID;
    public static final String FILTER_COL_PK = SQL_COL_PK + "= ?";
    public static final String SQL_COL_DISPLAY_TEXT = "disp_txt";
    public static final String SQL_COL_LAT = MediaStore.Images.Media.LATITUDE;
    public static final String SQL_COL_LON = MediaStore.Images.Media.LONGITUDE;

    // new col id for with since ver 0.6.3
    public static final String SQL_COL_WIDTH = "col_width";
    // since ver 0.6.3: file size. old col id for image-with before ver 0.6.3
    public static final String SQL_COL_SIZE = MediaStore.Images.Media.SIZE;

    // in seconds since 1970
    public static final String SQL_COL_DATE_ADDED = MediaStore.Images.ImageColumns.DATE_ADDED;

    // other colums
    // in seconds since 1970
    public static final String SQL_COL_LAST_MODIFIED = MediaStore.MediaColumns.DATE_MODIFIED;

    public static final String SQL_COL_GPS = MediaStore.Images.Media.LONGITUDE;
    public static final String SQL_COL_COUNT = "count";
    public static final String SQL_COL_WHERE_PARAM = "where_param";

    // in milli-seconds since 1970
    public static final String SQL_COL_DATE_TAKEN = MediaStore.Images.Media.DATE_TAKEN;
    public static final String SQL_COL_EXT_RATING = MediaStore.Video.Media.BOOKMARK;
    public static final String SQL_COL_PATH = MediaStore.Images.Media.DATA;

    // either code 0..8 or rotation angle 0, 90, 180, 270
    public static final String SQL_COL_ORIENTATION = MediaStore.Images.ImageColumns.ORIENTATION;

    // only works with api >= 16
    public static final String SQL_COL_MAX_WITH =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                ? "max(" + MediaStore.Images.Media.WIDTH + "," +
                                                MediaStore.Images.Media.HEIGHT +")"
                : "1024";


    public static final String SQL_COL_EXT_MEDIA_TYPE = MediaStore.Files.FileColumns.MEDIA_TYPE;
    public static final int MEDIA_TYPE_IMAGE = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;   // 1

    // used to translate between LAST_MODIFIED in database (secs since 1970) and internal format (java date milli secs simce 1970)
    private static final int LAST_MODIFIED_FACTOR = 1000;

    public static final int MEDIA_TYPE_IMAGE_PRIVATE = 1000 + MEDIA_TYPE_IMAGE;                 // 1001 APhoto manager specific
    public static final int MEDIA_TYPE_ALBUM_FILE    = 0;

    protected static final String FILTER_EXPR_PRIVATE
            = "(" + SQL_COL_EXT_MEDIA_TYPE + " = " + MEDIA_TYPE_IMAGE_PRIVATE + ")";
    protected static final String FILTER_EXPR_PRIVATE_PUBLIC
            = "(" + SQL_COL_EXT_MEDIA_TYPE + " in (" + MEDIA_TYPE_IMAGE_PRIVATE + "," + MEDIA_TYPE_IMAGE +"))";
    protected static final String FILTER_EXPR_PUBLIC
            = "(" + SQL_COL_EXT_MEDIA_TYPE + " = " + MEDIA_TYPE_IMAGE + ")";

    private static final String FILTER_EXPR_LAT_MAX = SQL_COL_LAT + " < ?";
    private static final String FILTER_EXPR_LAT_MIN = SQL_COL_LAT + " >= ?";
    private static final String FILTER_EXPR_NO_GPS = SQL_COL_LAT + " is null AND " + SQL_COL_LON + " is null";
    private static final String FILTER_EXPR_LON_MAX = SQL_COL_LON + " < ?";
    private static final String FILTER_EXPR_LON_MIN = SQL_COL_LON + " >= ?";
    protected static final String FILTER_EXPR_RATING_MIN = SQL_COL_EXT_RATING + " >= ?";

    // SQL_COL_DATE_TAKEN and "?" in milli-seconds since 1970
    private static final String FILTER_EXPR_DATE_MAX = SQL_COL_DATE_TAKEN + " < ?";
    private static final String FILTER_EXPR_DATE_MIN = SQL_COL_DATE_TAKEN + " >= ?";

    // SQL_COL_LAST_MODIFIED in seconds since 1970; "?" in milli-seconds since 1970
    private static final String FILTER_EXPR_DATE_MODIFIED_MAX = SQL_COL_LAST_MODIFIED + " < ?";
    private static final String FILTER_EXPR_DATE_MODIFIED_MIN = SQL_COL_LAST_MODIFIED + " >= ?";
    protected static final String FILTER_EXPR_PATH_LIKE = "(" + SQL_COL_PATH + " like ?)";

    // same format as dir. i.e. description='/2014/12/24/' or '/mnt/sdcard/pictures/'
    public static final String SQL_EXPR_DAY = "strftime('/%Y/%m/%d/', " + SQL_COL_DATE_TAKEN + " / " +
            LAST_MODIFIED_FACTOR + ", 'unixepoch', 'localtime')";
    public static final String SQL_EXPR_DAY_MODIFIED = "strftime('/%Y/%m/%d/', " + SQL_COL_LAST_MODIFIED + ",  'unixepoch', 'localtime')";

    public static final QueryParameter queryGroupByDate = new QueryParameter()
            .setID(QUERY_TYPE_GROUP_DATE)
            .addColumn(
                    "max(" + SQL_COL_PK + ") AS " + SQL_COL_PK,
                    SQL_EXPR_DAY + " AS " + SQL_COL_DISPLAY_TEXT,
                    "count(*) AS " + SQL_COL_COUNT,
                    "max(" + SQL_COL_GPS + ") AS " + SQL_COL_GPS,
                    "max(" + SQL_COL_PATH + ") AS " + SQL_COL_PATH)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            .addWhere(FILTER_EXPR_PRIVATE_PUBLIC)
            .addGroupBy(SQL_EXPR_DAY)
            .addOrderBy(SQL_EXPR_DAY);

    public static final QueryParameter queryGroupByDateModified = new QueryParameter()
            .setID(QUERY_TYPE_GROUP_DATE_MODIFIED)
            .addColumn(
                    "max(" + SQL_COL_PK + ") AS " + SQL_COL_PK,
                    SQL_EXPR_DAY_MODIFIED + " AS " + SQL_COL_DISPLAY_TEXT,
                    "count(*) AS " + SQL_COL_COUNT,
                    "max(" + SQL_COL_GPS + ") AS " + SQL_COL_GPS,
                    "max(" + SQL_COL_PATH + ") AS " + SQL_COL_PATH)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            .addWhere(FILTER_EXPR_PRIVATE_PUBLIC)
            .addGroupBy(SQL_EXPR_DAY_MODIFIED)
            .addOrderBy(SQL_EXPR_DAY_MODIFIED);

    public static final String SQL_EXPR_FOLDER = "substr(" + SQL_COL_PATH + ",1,length(" + SQL_COL_PATH + ") - length(" + MediaStore.Images.Media.DISPLAY_NAME + "))";
    public static final QueryParameter queryGroupByDir = new QueryParameter()
            .setID(QUERY_TYPE_GROUP_ALBUM)
            .addColumn(
                    "max(" + SQL_COL_PK + ") AS " + SQL_COL_PK,
                    SQL_EXPR_FOLDER + " AS " + SQL_COL_DISPLAY_TEXT,
                    "count(*) AS " + SQL_COL_COUNT,
                    // (Substr(_data,1, length(_data) -  length(_display_Name)) = '/storage/sdcard0/DCIM/onw7b/2013/')
                    // "'(" + SQL_EXPR_FOLDER + " = ''' || " + SQL_EXPR_FOLDER + " || ''')'"
                    "max(" + SQL_COL_GPS + ") AS " + SQL_COL_GPS)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            .addWhere(FILTER_EXPR_PRIVATE_PUBLIC)
            .addGroupBy(SQL_EXPR_FOLDER)
            .addOrderBy(SQL_EXPR_FOLDER);

    public static final QueryParameter queryVAlbum = new QueryParameter()
            .setID(QUERY_TYPE_GROUP_ALBUM)
            .addColumn(
                    SQL_COL_PK,
                    SQL_COL_PATH + " AS " + SQL_COL_DISPLAY_TEXT,
                    "0 AS " + SQL_COL_COUNT,
                    "null AS " + SQL_COL_GPS)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            .addWhere(SQL_COL_PATH + " like '%" + AlbumFile.SUFFIX_VALBUM + "'")
            .addOrderBy(SQL_COL_PATH);

    /* image entries may become duplicated if media scanner finds new images that have not been inserted into media database yet
     * and aFotoSql tries to show the new image and triggers a filescan. */
    public static final QueryParameter queryGetDuplicates = new QueryParameter()
            .setID(QUERY_TYPE_UNDEFINED)
            .addColumn(
                    "min(" + SQL_COL_PK + ") AS " + SQL_COL_PK,
                    SQL_COL_PATH + " AS " + SQL_COL_DISPLAY_TEXT,
                    "count(*) AS " + SQL_COL_COUNT)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            .addWhere(SQL_COL_PATH + " IS NOT NULL ")
            .addGroupBy(SQL_COL_PATH)
            .addHaving("count(*) > 1")
            .addOrderBy(SQL_COL_PATH);

    /* image entries may not have DISPLAY_NAME which is essential for calculating the item-s folder. */
    public static final QueryParameter queryChangePath = new QueryParameter()
            .setID(QUERY_TYPE_UNDEFINED)
            .addColumn(
                    SQL_COL_PK,
                    SQL_COL_PATH,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.TITLE)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            ;

    /* image entries may not have DISPLAY_NAME which is essential for calculating the item-s folder. */
    public static final QueryParameter queryGetMissingDisplayNames = new QueryParameter(queryChangePath)
            .addWhere(MediaStore.MediaColumns.DISPLAY_NAME + " is null")
            .addWhere(FILTER_EXPR_PRIVATE_PUBLIC)
            ;

    // the bigger the smaller the area
    private static final double GROUPFACTOR_FOR_Z0 = 0.025;

    /** to avoid cascade delete of linked file when mediaDB-item is deleted
     *  the links are first set to null before delete. */
    private static final String DELETED_FILE_MARKER = null;

    /**
     * translate from bytes to kilobytes
     */
    private static final int SIZE_K = 1024;
    /**
     * when image bigger than this translate image size
     * from bytes to kilobytes, from kilobytes to megabytes, ...
     */
    private static final int SIZE_TRANLATION_LIMIT = SIZE_K * 10;

    public static final double getGroupFactor(final double _zoomLevel) {
        double zoomLevel = _zoomLevel;
        double result = GROUPFACTOR_FOR_Z0;
        while (zoomLevel > 0) {
            // result <<= 2; //
            result = result * 2;
            zoomLevel--;
        }

        if (Global.debugEnabled) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.getGroupFactor(" + _zoomLevel + ") => " + result);
        }

        return result;
    }

    public static final QueryParameter queryGroupByPlace = getQueryGroupByPlace(100);

    public static QueryParameter getQueryGroupByPlace(double groupingFactor) {
        //String SQL_EXPR_LAT = "(round(" + SQL_COL_LAT + " - 0.00499, 2))";
        //String SQL_EXPR_LON = "(round(" + SQL_COL_LON + " - 0.00499, 2))";

        // "- 0.5" else rounding "10.6" becomes 11.0
        // + (1/groupingFactor/2) in the middle of grouping area
        String SQL_EXPR_LAT = "((round((" + SQL_COL_LAT + " * " + groupingFactor + ") - 0.5) /"
                + groupingFactor + ") + " + (1/groupingFactor/2) + ")";
        String SQL_EXPR_LON = "((round((" + SQL_COL_LON + " * " + groupingFactor + ") - 0.5) /"
                + groupingFactor + ") + " + (1/groupingFactor/2) + ")";

        QueryParameter result = new QueryParameter();

        result.setID(QUERY_TYPE_GROUP_PLACE)
                .addColumn(
                        "max(" + SQL_COL_PK + ") AS " + SQL_COL_PK,
                        SQL_EXPR_LAT + " AS " + SQL_COL_LAT,
                        SQL_EXPR_LON + " AS " + SQL_COL_LON,
                        "count(*) AS " + SQL_COL_COUNT)
                .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
                .addWhere(FILTER_EXPR_PRIVATE_PUBLIC)
                .addGroupBy(SQL_EXPR_LAT, SQL_EXPR_LON)
                .addOrderBy(SQL_EXPR_LAT, SQL_EXPR_LON);

        return result;
    }

    public static final String[] DEFAULT_GALLERY_COLUMNS = new String[]{SQL_COL_PK,
            SQL_COL_PATH + " AS " + SQL_COL_DISPLAY_TEXT,
            // "0 AS " + SQL_COL_COUNT,
            SQL_COL_MAX_WITH + " AS " + SQL_COL_WIDTH,
            SQL_COL_GPS,
            SQL_COL_DATE_TAKEN,
            SQL_COL_PATH,
            SQL_COL_ORIENTATION};

    public static final QueryParameter queryDetail = new QueryParameter()
            .setID(QUERY_TYPE_GALLERY)
            .addColumn(
                    DEFAULT_GALLERY_COLUMNS)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            ;

    // query ordered by DatePhotoTaken so that lower rename-numbers correspond to older images.
    public static final QueryParameter queryAutoRename = new QueryParameter()
            .setID(QUERY_TYPE_GALLERY)
            .addColumn(SQL_COL_PK, SQL_COL_PATH, SQL_COL_DATE_TAKEN, SQL_COL_LAST_MODIFIED)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            .addOrderBy(SQL_COL_DATE_TAKEN + " ASC", SQL_COL_LAST_MODIFIED + " ASC")
            ;

    public static final QueryParameter queryGps = new QueryParameter()
            .setID(QUERY_TYPE_UNDEFINED)
            .addColumn(
                    SQL_COL_PK,
                    // SQL_COL_PATH + " AS " + SQL_COL_DISPLAY_TEXT,
                    // "0 AS " + SQL_COL_COUNT,
                    SQL_COL_LAT, SQL_COL_LON)
            .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
            ;

    public static void filter2Query(QueryParameter resultQuery, IGalleryFilter filter, boolean clearWhereBefore) {
        if ((resultQuery != null) && (!GalleryFilterParameter.isEmpty(filter))) {
            if (clearWhereBefore) {
                resultQuery.clearWhere();
            }

            addWhereFilterLatLon(resultQuery, filter);

            addWhereDateMinMax(resultQuery, filter.getDateMin(), filter.getDateMax());
            addWhereDateModifiedMinMax(resultQuery, filter.getDateModifiedMin(), filter.getDateModifiedMax());

            String path = filter.getPath();
            if ((path != null) && (path.length() > 0)) resultQuery.addWhere(FILTER_EXPR_PATH_LIKE, path);
        }
    }

    public static void addWhereDateMinMax(QueryParameter resultQuery, final long dateMinInMilliSecs1970, final long dateMaxInMilliSecs1970) {

        // SQL_COL_DATE_TAKEN and "?" in milli-seconds since 1970 no translaton neccessary
        if (dateMinInMilliSecs1970 != 0) resultQuery.addWhere(FILTER_EXPR_DATE_MIN, Long.toString(dateMinInMilliSecs1970));

        if (dateMaxInMilliSecs1970 != 0) resultQuery.addWhere(FILTER_EXPR_DATE_MAX, Long.toString(dateMaxInMilliSecs1970));
    }

    public static void addWhereDateModifiedMinMax(QueryParameter resultQuery, final long dateMinInMilliSecs1970, final long dateMaxInMilliSecs1970) {

        // SQL_COL_LAST_MODIFIED in seconds since 1970; translate from MilliSecs
        if (dateMinInMilliSecs1970 != 0) resultQuery.addWhere(FILTER_EXPR_DATE_MODIFIED_MIN, Long.toString(dateMinInMilliSecs1970 / LAST_MODIFIED_FACTOR));

        if (dateMaxInMilliSecs1970 != 0) resultQuery.addWhere(FILTER_EXPR_DATE_MODIFIED_MAX, Long.toString(dateMaxInMilliSecs1970 / LAST_MODIFIED_FACTOR));
    }

    /** translates a query back to filter */
    public static IGalleryFilter parseQuery(QueryParameter query, boolean removeFromSourceQuery) {
        if (query != null) {
            GalleryFilterParameter filter = new GalleryFilterParameter();
            parseQueryGeo(query, filter, removeFromSourceQuery);

            filter.setRatingMin(GalleryFilterParameter.parseRating(getParam(query, FILTER_EXPR_RATING_MIN, removeFromSourceQuery)));
            filter.setDate(getParam(query, FILTER_EXPR_DATE_MIN, removeFromSourceQuery),
                    getParam(query, FILTER_EXPR_DATE_MAX, removeFromSourceQuery));
            filter.setPath(getFilePath(query, removeFromSourceQuery));

            // SQL_COL_LAST_MODIFIED in seconds since 1970; translate from MilliSecs
            filter.setDateModified(parseDateModifiedMin(query, removeFromSourceQuery),
                    parseDateModifiedMax(query, removeFromSourceQuery));

            return filter;
        }
        return null;
    }

    public static long parseDateModifiedMax(QueryParameter query, boolean removeFromSourceQuery) {
        return getParamWithFix(query, FILTER_EXPR_DATE_MODIFIED_MAX, removeFromSourceQuery, LAST_MODIFIED_FACTOR);
    }

    public static long parseDateModifiedMin(QueryParameter query, boolean removeFromSourceQuery) {
        return getParamWithFix(query, FILTER_EXPR_DATE_MODIFIED_MIN, removeFromSourceQuery, LAST_MODIFIED_FACTOR);
    }

    /** extracts geo infos from srcQuery to destFilter */
    public static GeoRectangle parseQueryGeo(QueryParameter srcQuery, GeoRectangle destFilter, boolean removeFromSourceQuery) {
        if (null != getParams(srcQuery, FILTER_EXPR_NO_GPS, removeFromSourceQuery)) {
            if (destFilter != null) destFilter.setNonGeoOnly(true);
        } else {
            final String lonMin = getParam(srcQuery, FILTER_EXPR_LON_MIN, removeFromSourceQuery);
            final String lonMax = getParam(srcQuery, FILTER_EXPR_LON_MAX, removeFromSourceQuery);
            final String latMin = getParam(srcQuery, FILTER_EXPR_LAT_MIN, removeFromSourceQuery);
            final String latMax = getParam(srcQuery, FILTER_EXPR_LAT_MAX, removeFromSourceQuery);
            if (destFilter != null) {
                destFilter.setLogitude(lonMin, lonMax);
                destFilter.setLatitude(latMin, latMax);
            }
        }
        return destFilter;
    }

    public static String getFilePath(QueryParameter query, boolean removeFromSourceQuery) {
        if (query == null) return null;
        return getParam(query, FILTER_EXPR_PATH_LIKE, removeFromSourceQuery);
    }

    /** append path expressions from src to dest. Return null if unchanged. */
    public static QueryParameter copyPathExpressions(QueryParameter dest, QueryParameter src) {
        QueryParameter resultQuery = null;
        if (src != null) {
            // duplicate will be modified. preserve original
            QueryParameter remainingQuery = new QueryParameter(src);
            String pathExpr = null;
            while (true) {
                pathExpr = getFilePath(remainingQuery, true);
                if (pathExpr != null) {
                    if (resultQuery == null) resultQuery = new QueryParameter(dest);
                    resultQuery.addWhere(FILTER_EXPR_PATH_LIKE, pathExpr);
                } else {
                    break;
                }
            }
        }
        return resultQuery;
    }

    /** @return return param for expression inside query. null if expression is not in query or number of params is not 1. */
    protected static String getParam(QueryParameter query, String expresion, boolean removeFromSourceQuery) {
        final String[] result = getParams(query, expresion, removeFromSourceQuery);
        return ((result != null) && (result.length > 0)) ? result[0] : null;
    }

    /** @return return param for expression inside query. null if expression is not in query or number of params is not 1. */
    protected static long getParamWithFix(QueryParameter query, String expresion, boolean removeFromSourceQuery, long factor) {
        try {
            String svalue = getParam(query, expresion, removeFromSourceQuery);
            long lvalue = Long.parseLong(svalue);
            return lvalue * factor;
        } catch (Exception ignore) {

        }
        return 0;
    }

    /** @return return all params for expression inside query. null if expression is not in query */
    protected static String[] getParams(QueryParameter query, String expresion, boolean removeFromSourceQuery) {
        return query.getWhereParameter(expresion, removeFromSourceQuery);
    }

    public static QueryParameter setWhereSelectionPks(QueryParameter query, SelectedItems selectedItems) {
        if ((query != null) && (selectedItems != null) && (!selectedItems.isEmpty())) {
            String pksAsListString = selectedItems.toString();
            setWhereSelectionPks(query, pksAsListString);
        }
        return query;
    }

    public static QueryParameter setWhereSelectionPks(QueryParameter query, String pksAsListString) {
        if ((pksAsListString != null) && (pksAsListString.length() > 0)) {
            query.clearWhere()
                    .addWhere(FotoSql.SQL_COL_PK + " in (" + pksAsListString + ")")
            ;
        }
        return query;
    }

    public static void setWhereSelectionPaths(QueryParameter query, SelectedFiles selectedItems) {
        if ((query != null) && (selectedItems != null) && (selectedItems.size() > 0)) {
            query.clearWhere()
                    .addWhere(FotoSql.SQL_COL_PATH + " in (" + selectedItems.toString() + ")")
            ;
        }
    }

    public static void setWhereFileNames(QueryParameter query, String... fileNames) {
        if ((query != null) && (fileNames != null) && (fileNames.length > 0)) {
            query.clearWhere()
                    .addWhere(getWhereInFileNames(fileNames))
            ;
        }
    }

    public static void addWhereLatLonNotNull(QueryParameter query) {
        query.addWhere(FotoSql.SQL_COL_LAT + " is not null and "
                + FotoSql.SQL_COL_LON + " is not null");
    }

    public static void addWhereFilterLatLon(QueryParameter resultQuery, IGeoRectangle filter) {
        if ((resultQuery != null) && (filter != null)) {
            if (filter.isNonGeoOnly()) {
                resultQuery.addWhere(FILTER_EXPR_NO_GPS);
            } else {
                addWhereFilterLatLon(resultQuery, filter.getLatitudeMin(),
                        filter.getLatitudeMax(), filter.getLogituedMin(), filter.getLogituedMax());
            }
        }
    }

    public static void addWhereFilterLatLon(QueryParameter query, double latitudeMin, double latitudeMax, double logituedMin, double logituedMax) {
        if (!Double.isNaN(latitudeMin)) query.addWhere(FILTER_EXPR_LAT_MIN, DirectoryFormatter.formatLatLon(latitudeMin));
        if (!Double.isNaN(latitudeMax)) query.addWhere(FILTER_EXPR_LAT_MAX, DirectoryFormatter.formatLatLon(latitudeMax));
        if (!Double.isNaN(logituedMin)) query.addWhere(FILTER_EXPR_LON_MIN, DirectoryFormatter.formatLatLon(logituedMin));
        if (!Double.isNaN(logituedMax)) query.addWhere(FILTER_EXPR_LON_MAX, DirectoryFormatter.formatLatLon(logituedMax));
    }

    public static void addPathWhere(QueryParameter newQuery, String selectedAbsolutePath, int dirQueryID) {
        if ((selectedAbsolutePath != null) && (selectedAbsolutePath.length() > 0)) {
            switch (dirQueryID) {
                case QUERY_TYPE_GROUP_DATE:
                    addWhereDatePath(newQuery, selectedAbsolutePath);
                    break;
                case QUERY_TYPE_GROUP_DATE_MODIFIED:
                    addWhereDateModifiedPath(newQuery, selectedAbsolutePath);
                    break;
                default:
                    // selectedAbsolutePath is assumed to be a file path i.e. /mnt/sdcard/pictures/
                    addWhereDirectoryPath(newQuery, selectedAbsolutePath);
            }
        }
    }

    /**
     * directory path i.e. /mnt/sdcard/pictures/
     */
    private static void addWhereDirectoryPath(QueryParameter newQuery, String selectedAbsolutePath) {
        if (FotoViewerParameter.includeSubItems) {
            newQuery
                    .addWhere(FotoSql.SQL_COL_PATH + " like ?", selectedAbsolutePath + "%")
                            // .addWhere(FotoSql.SQL_COL_PATH + " like '" + selectedAbsolutePath + "%'")
                    .addOrderBy(FotoSql.SQL_COL_PATH);
        } else {
            // foldername exact match
            newQuery
                    .addWhere(SQL_EXPR_FOLDER + " =  ?", selectedAbsolutePath)
                    .addOrderBy(FotoSql.SQL_COL_PATH);
        }
    }

    /**
     * path has format /year/month/day/ or /year/month/ or /year/ or /
     */
    private static void addWhereDatePath(QueryParameter newQuery, String selectedAbsolutePath) {
        addWhereDatePath(newQuery, selectedAbsolutePath, SQL_COL_DATE_TAKEN, FILTER_EXPR_DATE_MIN, FILTER_EXPR_DATE_MAX);
    }

    /**
     * path has format /year/month/day/ or /year/month/ or /year/ or /
     */
    private static void addWhereDateModifiedPath(QueryParameter newQuery, String selectedAbsolutePath) {
        addWhereDatePath(newQuery, selectedAbsolutePath, SQL_COL_LAST_MODIFIED, FILTER_EXPR_DATE_MODIFIED_MIN, FILTER_EXPR_DATE_MODIFIED_MAX);
    }

    private static void addWhereDatePath(QueryParameter newQuery, String selectedAbsolutePath, String sqlColDate, String filterExprDateMin, String filterExprDateMax) {
        Date from = new Date();
        Date to = new Date();

        DirectoryFormatter.parseDatesPath(selectedAbsolutePath, from, to);

        if (to.getTime() == 0) {
            newQuery
                    .addWhere(sqlColDate + " in (0,-1, null)")
                    .addOrderBy(sqlColDate + " desc");
        } else {
            newQuery
                    .addWhere(filterExprDateMin, "" + from.getTime())
                    .addWhere(filterExprDateMax, "" + to.getTime())
                    .addOrderBy(sqlColDate + " desc");
        }
    }

    public static QueryParameter getQuery(int queryID) {
        switch (queryID) {
            case QUERY_TYPE_UNDEFINED:
                return null;
            case QUERY_TYPE_GALLERY:
                return queryDetail;
            case QUERY_TYPE_GROUP_DATE:
                return queryGroupByDate;
            case QUERY_TYPE_GROUP_DATE_MODIFIED:
                return queryGroupByDateModified;
            case QUERY_TYPE_GROUP_ALBUM:
                return queryGroupByDir;
            case QUERY_TYPE_GROUP_PLACE:
            case QUERY_TYPE_GROUP_PLACE_MAP:
                return queryGroupByPlace;
            case QUERY_TYPE_GROUP_COPY:
            case QUERY_TYPE_GROUP_MOVE:
                return null;
            default:
                Log.e(Global.LOG_CONTEXT, "FotoSql.getQuery(" + queryID + "): unknown ID");
                return null;
        }
    }

    public static String getName(Context context, int id) {
        switch (id) {
            case IGalleryFilter.SORT_BY_NONE_OLD:
            case IGalleryFilter.SORT_BY_NONE:
                return context.getString(R.string.sort_by_none);
            case SORT_BY_DATE_OLD:
            case SORT_BY_DATE:
                return context.getString(R.string.sort_by_date);
            case SORT_BY_NAME_OLD:
            case SORT_BY_NAME:
                return context.getString(R.string.sort_by_name);
            case SORT_BY_LOCATION_OLD:
            case SORT_BY_LOCATION:
                return context.getString(R.string.sort_by_place);
            case SORT_BY_FILE_LEN:
                return context.getString(R.string.sort_by_file_size);

            case SORT_BY_WIDTH:
                return context.getString(R.string.sort_by_width);

            case SORT_BY_NAME_LEN_OLD:
            case SORT_BY_NAME_LEN:
                return context.getString(R.string.sort_by_name_len);

            case SORT_BY_RATING:
                return context.getString(R.string.sort_by_rating);
            case SORT_BY_MODIFICATION:
                return context.getString(R.string.sort_by_modification);

            case QUERY_TYPE_GALLERY:
                return context.getString(R.string.gallery_title);
            case QUERY_TYPE_GROUP_DATE:
                return context.getString(R.string.sort_by_date);
            case QUERY_TYPE_GROUP_DATE_MODIFIED:
                return context.getString(R.string.sort_by_modification);

            case QUERY_TYPE_GROUP_ALBUM:
                return context.getString(R.string.sort_by_folder);
            case QUERY_TYPE_GROUP_PLACE:
            case QUERY_TYPE_GROUP_PLACE_MAP:
                return context.getString(R.string.sort_by_place);
            case QUERY_TYPE_GROUP_COPY:
                return context.getString(R.string.destination_copy);
            case QUERY_TYPE_GROUP_MOVE:
                return context.getString(R.string.destination_move);
            case QUERY_TYPE_TAG:
                return context.getString(R.string.lbl_tag);
            default:
                return "???";
        }

    }

    public static QueryParameter setSort(QueryParameter result, int sortID, boolean ascending) {
        String asc = (ascending) ? " asc" : " desc";
        result.replaceOrderBy();
        switch (sortID) {
            case SORT_BY_DATE_OLD:
            case SORT_BY_DATE:
                return result.replaceOrderBy(SQL_COL_DATE_TAKEN + asc);

            case SORT_BY_MODIFICATION:
                return result.replaceOrderBy(SQL_COL_LAST_MODIFIED + asc);

            case SORT_BY_NAME_OLD:
            case SORT_BY_NAME:
                return result.replaceOrderBy(SQL_COL_PATH + asc);

            case SORT_BY_RATING:
                return result.replaceOrderBy(SQL_COL_EXT_RATING  + asc, SQL_COL_DATE_TAKEN + asc);

            case SORT_BY_FILE_LEN:
                return result.replaceOrderBy(SQL_COL_SIZE + asc);

            case SORT_BY_WIDTH:
                return result.replaceOrderBy(SQL_COL_MAX_WITH + asc);

            case SORT_BY_LOCATION_OLD:
            case SORT_BY_LOCATION:
                return result.replaceOrderBy(SQL_COL_GPS + asc, MediaStore.Images.Media.LATITUDE + asc);
            case SORT_BY_NAME_LEN_OLD:
            case SORT_BY_NAME_LEN:
                return result.replaceOrderBy("length(" + SQL_COL_PATH + ")" + asc, SQL_COL_PATH + asc);
            default: return  result;
        }
    }

    public static boolean set(GalleryFilterParameter dest, String selectedAbsolutePath, int queryTypeId) {
        switch (queryTypeId) {
            case FotoSql.QUERY_TYPE_GROUP_ALBUM:
            case FotoSql.QUERY_TYPE_GROUP_MOVE:
            case FotoSql.QUERY_TYPE_GROUP_COPY:
            case QUERY_TYPE_GALLERY:
                dest.setPath(selectedAbsolutePath + "/%");
                return true;
            case FotoSql.QUERY_TYPE_GROUP_DATE: {
                Date from = new Date();
                Date to = new Date();

                DirectoryFormatter.parseDatesPath(selectedAbsolutePath, from, to);
                dest.setDate(from.getTime(), to.getTime());
                return true;
            }
            case FotoSql.QUERY_TYPE_GROUP_DATE_MODIFIED: {
                Date from = new Date();
                Date to = new Date();

                DirectoryFormatter.parseDatesPath(selectedAbsolutePath, from, to);
                dest.setDateModified(from.getTime(), to.getTime());
                return true;
            }
            case FotoSql.QUERY_TYPE_GROUP_PLACE_MAP:
            case FotoSql.QUERY_TYPE_GROUP_PLACE:
                IGeoRectangle geo = DirectoryFormatter.parseLatLon(selectedAbsolutePath);
                if (geo != null) {
                    dest.get(geo);
                }
                return true;
            default:break;
        }
        Log.e(Global.LOG_CONTEXT, "FotoSql.setFilter(queryTypeId = " + queryTypeId + ") : unknown type");

        return false;
    }

	/** converts content-Uri-with-id to full path */
    public static String execGetFotoPath(Context context, Uri uriWithID) {
        Cursor c = null;
        try {
            c = createCursorForQuery(null, "execGetFotoPath(uri)", context, uriWithID.toString(), null, null, null, FotoSql.SQL_COL_PATH);
            if (c.moveToFirst()) {
                return DBUtils.getString(c,FotoSql.SQL_COL_PATH, null);
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.execGetFotoPath() Cannot get path from " + uriWithID, ex);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

	/** search for all full-image-file-paths that matches pathfilter  */
    public static List<String> execGetFotoPaths(Context context, String pathFilter) {
        ArrayList<String> result = new ArrayList<String>();

        Cursor c = null;
        try {
            c = createCursorForQuery(null, "execGetFotoPaths(pathFilter)", context,SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME,
                        FotoSql.SQL_COL_PATH + " like ? and " + FILTER_EXPR_PRIVATE_PUBLIC,
                        new String[]{pathFilter}, FotoSql.SQL_COL_PATH, FotoSql.SQL_COL_PATH);
            while (c.moveToNext()) {
                result.add(c.getString(0));
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.execGetFotoPaths() Cannot get path from: " + FotoSql.SQL_COL_PATH + " like '" + pathFilter +"'", ex);
        } finally {
            if (c != null) c.close();
        }

        if (Global.debugEnabled) {
            Log.d(Global.LOG_CONTEXT, "FotoSql.execGetFotoPaths() result count=" + result.size());
        }
        return result;
    }

    public static Cursor createCursorForQuery(
            StringBuilder out_debugMessage, String dbgContext, final Context context,
            QueryParameter parameters, VISIBILITY visibility) {
        if (visibility != null) setWhereVisibility(parameters, visibility);
        return createCursorForQuery(out_debugMessage, dbgContext, context, parameters.toFrom(), parameters.toAndroidWhere(),
                parameters.toAndroidParameters(), parameters.toOrderBy(),
                parameters.toColumns()
        );
    }

    /** every cursor query should go through this. adds logging if enabled */
    private static Cursor createCursorForQuery(StringBuilder out_debugMessage, String dbgContext, final Context context, final String from, final String sqlWhereStatement,
                                               final String[] sqlWhereParameters, final String sqlSortOrder,
                                               final String... sqlSelectColums) {
        ContentResolver resolver = context.getContentResolver();
        Cursor query = null;

        Exception excpetion = null;
        try {
            query = resolver.query(Uri.parse(from), sqlSelectColums, sqlWhereStatement, sqlWhereParameters, sqlSortOrder);
        } catch (Exception ex) {
            excpetion = ex;
        } finally {
            if ((excpetion != null) || Global.debugEnabledSql || (out_debugMessage != null)) {
                StringBuilder message = StringUtils.appendMessage(out_debugMessage, excpetion,
                        dbgContext,"FotoSql.createCursorForQuery:\n" ,
                        QueryParameter.toString(sqlSelectColums, null, from, sqlWhereStatement,
                                sqlWhereParameters, sqlSortOrder, query.getCount()));
                if (out_debugMessage == null) {
                    Log.i(Global.LOG_CONTEXT, message.toString(), excpetion);
                } // else logging is done by caller
            }
        }

        return query;
    }

    public static IGeoRectangle execGetGeoRectangle(StringBuilder out_debugMessage, Context context, QueryParameter baseQuery,
                                                    SelectedItems selectedItems, Object... dbgContext) {
        StringBuilder debugMessage = (out_debugMessage == null)
                ? StringUtils.createDebugMessage(Global.debugEnabledSql, dbgContext)
                : out_debugMessage;
        QueryParameter query = new QueryParameter()
                .setID(QUERY_TYPE_UNDEFINED)
                .addColumn(
                        "min(" + SQL_COL_LAT + ") AS LAT_MIN",
                        "max(" + SQL_COL_LAT + ") AS LAT_MAX",
                        "min(" + SQL_COL_LON + ") AS LON_MIN",
                        "max(" + SQL_COL_LON + ") AS LON_MAX",
                        "count(*)"
                )
                .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
                ;

        if (baseQuery != null) {
            query.getWhereFrom(baseQuery, true);
        }

        if (selectedItems != null) {
            setWhereSelectionPks(query, selectedItems);
        }
        FotoSql.addWhereLatLonNotNull(query);

        GeoRectangle result = null;
        Cursor c = null;
        try {
            c = createCursorForQuery(debugMessage, "execGetGeoRectangle", context, query, VISIBILITY.PRIVATE_PUBLIC);
            if (c.moveToFirst()) {
                result = new GeoRectangle();
                result.setLatitude(c.getDouble(0), c.getDouble(1));
                result.setLogitude(c.getDouble(2), c.getDouble(3));

                return result;
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.execGetGeoRectangle(): error executing " + query, ex);
        } finally {
            if (c != null) c.close();
            if (debugMessage != null) {
                StringUtils.appendMessage(debugMessage,  "result", result);
                if (out_debugMessage == null) {
                    Log.i(Global.LOG_CONTEXT, debugMessage.toString());
                }
            }
        }
        return result;
    }

    /** gets IGeoPoint either from file if fullPath is not null else from db via id */
    public static IGeoPoint execGetPosition(StringBuilder out_debugMessage, Context context,
                                            String fullPath, long id, Object... dbgContext) {
        StringBuilder debugMessage = (out_debugMessage == null) ? StringUtils.createDebugMessage(Global.debugEnabledSql, dbgContext) : out_debugMessage;
        QueryParameter query = new QueryParameter()
        .setID(QUERY_TYPE_UNDEFINED)
                .addColumn(SQL_COL_LAT, SQL_COL_LON)
                .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
                .addWhere(SQL_COL_LAT + " IS NOT NULL")
                .addWhere(SQL_COL_LON + " IS NOT NULL")
                .addWhere("(" + SQL_COL_LON + " <> 0 OR " + SQL_COL_LAT + " <> 0)");

        if (fullPath != null) {
            query.addWhere(SQL_COL_PATH + "= ?", fullPath);

        } else {
            query.addWhere(FILTER_COL_PK, "" + id);
        }

        GeoPoint result = null;
        Cursor c = null;
        try {
            c = createCursorForQuery(debugMessage, "execGetPosition", context, query, VISIBILITY.PRIVATE_PUBLIC);
            if (c.moveToFirst()) {
                result = new GeoPoint(c.getDouble(0),c.getDouble(1));
                return result;
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.execGetPosition: error executing " + query, ex);
        } finally {
            if (c != null) c.close();
            if (debugMessage != null) {
                StringUtils.appendMessage(debugMessage,  "result", result);
                if (out_debugMessage == null) {
                    Log.i(Global.LOG_CONTEXT, debugMessage.toString());
                } // else logging by caller
            }
        }
        return null;
    }

    /**
     * @return returns a hashmap filename => mediaID
     */
    public static Map<String, Long> execGetPathIdMap(Context context, String... fileNames) {
        Map<String, Long> result = new HashMap<String, Long>();

        String whereFileNames = getWhereInFileNames(fileNames);
        if (whereFileNames != null) {
            QueryParameter query = new QueryParameter()
                    .setID(QUERY_TYPE_UNDEFINED)
                    .addColumn(SQL_COL_PK, SQL_COL_PATH)
                    .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
                    .addWhere(whereFileNames);

            Cursor c = null;
            try {
                c = createCursorForQuery(null, "execGetPathIdMap", context, query, null);
                while (c.moveToNext()) {
                    result.put(c.getString(1),c.getLong(0));
                }
            } catch (Exception ex) {
                Log.e(Global.LOG_CONTEXT, "FotoSql.execGetPathIdMap: error executing " + query, ex);
            } finally {
                if (c != null) c.close();
            }
        }
        return result;
    }

    public static String getWhereInFileNames(String... fileNames) {
        if (fileNames != null) {
            StringBuilder filter = new StringBuilder();
            filter.append(SQL_COL_PATH).append(" in (");

            int count = 0;
            for (String fileName : fileNames) {
                if ((fileName != null) &&!FileCommands.isSidecar(fileName)) {
                    if (count > 0) filter.append(", ");
                    filter.append("'").append(fileName).append("'");
                    count++;
                }
            }

            filter.append(")");

            if (count > 0) return filter.toString();
        }
        return null;
    }

    public static int execUpdate(String dbgContext, Context context, long id, ContentValues values) {
        return exexUpdateImpl(dbgContext, context, values, FILTER_COL_PK, new String[]{Long.toString(id)});
    }

    public static int execUpdate(String dbgContext, Context context, String path, ContentValues values, VISIBILITY visibility) {
        return exexUpdateImpl(dbgContext, context, values, getFilterExprPathLikeWithVisibility(visibility), new String[]{path});
    }

    /**
     * execRenameFolder(getActivity(),"/storage/sdcard0/testFolder/", "/storage/sdcard0/renamedFolder/")
     *    "/storage/sdcard0/testFolder/image.jpg" becomes "/storage/sdcard0/renamedFolder/image.jpg"
     * @return number of updated items
     */
    public static int execRenameFolder(Context context, String pathOld, String pathNew) {
        final String dbgContext = "FotoSql.execRenameFolder('" +
                pathOld + "' => '" + pathNew + "')";
        // sql update file set path = newBegin + substing(path, begin+len) where path like newBegin+'%'
        // public static final String SQL_EXPR_FOLDER = "substr(" + SQL_COL_PATH + ",1,length(" + SQL_COL_PATH + ") - length(" + MediaStore.Images.Media.DISPLAY_NAME + "))";

        final String sqlColNewPathAlias = "new_path";
        final String sql_col_pathnew = "'" + pathNew + "' || substr(" + SQL_COL_PATH +
            "," + (pathOld.length() + 1) + ",255) AS " + sqlColNewPathAlias;

        QueryParameter queryAffectedFiles = new QueryParameter()
                .setID(QUERY_TYPE_DEFAULT)
                .addColumn(SQL_COL_PK,
                        sql_col_pathnew)
                .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
                .addWhere(SQL_COL_PATH + " like '" + pathOld + "%'")
                // SQL_COL_EXT_MEDIA_TYPE IS NOT NULL enshures that all media types (mp3, mp4, txt,...) are updated
                .addWhere(SQL_COL_EXT_MEDIA_TYPE + " IS NOT NULL")
                ;

        SelectedFiles selectedFiles= getSelectedfiles(context, queryAffectedFiles, sqlColNewPathAlias, null);

        String[] paths = selectedFiles.getFileNames();
        Long[] ids = selectedFiles.getIds();
        ContentValues values = new ContentValues();
        String[] selectionArgs = new String[1];
        String _dbgContext = dbgContext + "(" +
                ids.length + " times)";
        for (int i = 0; i < ids.length; i++) {
            values.put(SQL_COL_PATH, paths[i]);
            selectionArgs[0] = ids[i].toString();
            if (exexUpdateImpl(_dbgContext, context, values, FILTER_COL_PK, selectionArgs) < 0) return -1;
            _dbgContext = null;
        }
        return ids.length;
    }

    /**
     * execRenameFolder(getActivity(),"/storage/sdcard0/testFolder/", "/storage/sdcard0/renamedFolder/")
     *    "/storage/sdcard0/testFolder/image.jpg" becomes "/storage/sdcard0/renamedFolder/image.jpg"
     * @return number of updated items
     */
    private static int _del_execRenameFolder_batch_not_working(Context context, String pathOld, String pathNew) {
        final String dbgContext = "FotoSql.execRenameFolder('" +
                pathOld + "' => '" + pathNew + "')";
        // sql update file set path = newBegin + substing(path, begin+len) where path like newBegin+'%'
        // public static final String SQL_EXPR_FOLDER = "substr(" + SQL_COL_PATH + ",1,length(" + SQL_COL_PATH + ") - length(" + MediaStore.Images.Media.DISPLAY_NAME + "))";

        final String sqlColNewPathAlias = "new_path";
        final String sql_col_pathnew = "'" + pathNew + "' || substr(" + SQL_COL_PATH +
                "," + (pathOld.length() + 1) + ",255) AS " + sqlColNewPathAlias;

        QueryParameter queryAffectedFiles = new QueryParameter()
                .setID(QUERY_TYPE_DEFAULT)
                .addColumn(SQL_COL_PK,
                        sql_col_pathnew)
                .addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME)
                .addWhere(SQL_COL_PATH + " like '" + pathOld + "%'")
                // SQL_COL_EXT_MEDIA_TYPE IS NOT NULL enshures that all media types (mp3, mp4, txt,...) are updated
                .addWhere(SQL_COL_EXT_MEDIA_TYPE + " IS NOT NULL")
                ;

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        Cursor c = null;
        try {
            c = createCursorForQuery(null, dbgContext, context, queryAffectedFiles, null);
            int pkColNo = c.getColumnIndex(FotoSql.SQL_COL_PK);
            int pathColNo = c.getColumnIndex(sqlColNewPathAlias);

            while (c.moveToNext()) {
                // paths[row] = c.getString(pathColNo);
                // ids[row] = c.getLong(pkColNo);
                ops.add(ContentProviderOperation.newUpdate(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE)
                        .withSelection(FILTER_COL_PK, new String[]{c.getString(pkColNo)})
                        .withValue(SQL_COL_PATH, c.getString(pathColNo))
                        .build());
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, dbgContext + "-getAffected error :", ex);
            return -1;
        } finally {
            if (c != null) c.close();
        }

        try {
            context.getContentResolver().applyBatch(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME, ops);
        } catch (Exception ex) {
            // java.lang.IllegalArgumentException: Unknown authority content://media/external/file
            // i assume not batch support for file
            Log.e(Global.LOG_CONTEXT, dbgContext + "-updateAffected error :", ex);
            return -1;
        }
        return ops.size();
    }

    /** every database update should go through this. adds logging if enabled */
    protected static int exexUpdateImpl(String dbgContext, Context context, ContentValues values, String sqlWhere, String[] selectionArgs) {
        int result = -1;
        Exception excpetion = null;
        try {
            result = context.getContentResolver().update(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE,
                    values, sqlWhere,
                    selectionArgs);
        } catch (Exception ex) {
            excpetion = ex;
        } finally {
            if ((excpetion != null) || ((dbgContext != null) && (Global.debugEnabledSql || LibGlobal.debugEnabledJpg))) {
                Log.i(Global.LOG_CONTEXT, dbgContext + ":FotoSql.exexUpdate " + excpetion + "\n" +
                        QueryParameter.toString(null, values.toString(), SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME,
                        sqlWhere, selectionArgs, null, result), excpetion);
            }
        }
        return result;
    }

    protected static String getFilterExprPathLikeWithVisibility(VISIBILITY visibility) {
        // visibility VISIBILITY.PRIVATE_PUBLIC
        String resultExpression = FotoSql.FILTER_EXPR_PATH_LIKE;
        if (visibility != null) {
            resultExpression += " AND " + getFilterExpressionVisibility(visibility);
        }
        return resultExpression;
    }

    /** return id of inserted item */
    public static Long insertOrUpdateMediaDatabase(String dbgContext, Context context,
                                                   String dbUpdateFilterJpgFullPathName,
                                                   ContentValues values, VISIBILITY visibility,
                                                   Long updateSuccessValue) {
        Long result = updateSuccessValue;

        int modifyCount =  FotoSql.execUpdate(dbgContext, context, dbUpdateFilterJpgFullPathName,
                values, visibility);

        if (modifyCount == 0) {
            // update failed (probably becauce oldFullPathName not found. try insert it.
            FotoSql.addDateAdded(values);

            Uri uriWithId =  FotoSql.execInsert(dbgContext, context, values);
            result = getId(uriWithId);
        }
        return result;
    }

    /** every database insert should go through this. adds logging if enabled */
    public static Uri execInsert(String dbgContext, Context context, ContentValues values) {
        Uri providerUri = (null != values.get(SQL_COL_EXT_MEDIA_TYPE)) ? SQL_TABLE_EXTERNAL_CONTENT_URI_FILE : SQL_TABLE_EXTERNAL_CONTENT_URI;

        Uri result = null;
        Exception excpetion = null;
        try {
        // on my android-4.4 insert with media_type=1001 (private) does insert with media_type=1 (image)
            result = context.getContentResolver().insert(providerUri, values);
        } catch (Exception ex) {
            excpetion = ex;
        } finally {
            if ((excpetion != null) || Global.debugEnabledSql || LibGlobal.debugEnabledJpg) {
                Log.i(Global.LOG_CONTEXT, dbgContext + ":FotoSql.execInsert " + excpetion + " " +
                        values.toString() + " => " + result + " " + excpetion, excpetion);
            }
        }
        return result;
    }

    @NonNull
    public static CursorLoader createCursorLoader(Context context, final QueryParameter query) {
        FotoSql.setWhereVisibility(query, VISIBILITY.DEFAULT);
        final CursorLoader loader = new CursorLoaderWithException(context, query);
        return loader;
    }

    public static int execDeleteByPath(String dbgContext, Activity context, String parentDirString, VISIBILITY visibility) {
        int delCount = FotoSql.deleteMedia(dbgContext, context, getFilterExprPathLikeWithVisibility(visibility), new String[] {parentDirString + "/%"}, true);
        return delCount;
    }

    public static int deleteMedia(String dbgContext, Context context, List<String> pathsToBeRemoved,
                                  boolean preventDeleteImageFile) {
        if ((pathsToBeRemoved != null) && (pathsToBeRemoved.size() > 0)) {
            String whereDelete = SQL_COL_PATH + " in ('" + ListUtils.toString("','", pathsToBeRemoved) + "')";
            return deleteMedia(dbgContext, context, whereDelete, null, preventDeleteImageFile);
        }
        return 0;
    }

    public static int deleteMediaWithNullPath(Context context) {
        /// delete where SQL_COL_PATH + " is null" throws null pointer exception
        QueryParameter wherePathIsNull = new QueryParameter();
        wherePathIsNull.addWhere(SQL_COL_PATH + " is null");
        wherePathIsNull.addWhere(FILTER_EXPR_PRIVATE_PUBLIC);

        // return deleteMedia("delete without path (_data = null)", context, wherePathIsNull.toAndroidWhere(), null, false);

        SelectedFiles filesWitoutPath = getSelectedfiles(context, wherePathIsNull, FotoSql.SQL_COL_PATH, VISIBILITY.PRIVATE_PUBLIC);
        String pksAsString = filesWitoutPath.toIdString();
        if ((pksAsString != null) && (pksAsString.length() > 0)) {
            QueryParameter whereInIds = new QueryParameter();
            FotoSql.setWhereSelectionPks(whereInIds, pksAsString);

            return deleteMedia("delete without path (_data = null)", context, whereInIds.toAndroidWhere(), null, true);
        }
        return 0;
    }
    /**
     * Deletes media items specified by where with the option to prevent cascade delete of the image.
     */
    public static int deleteMedia(String dbgContext, Context context, String where, String[] selectionArgs, boolean preventDeleteImageFile)
    {
        String[] lastSelectionArgs = selectionArgs;
        String lastUsedWhereClause = where;
        int delCount = 0;
        try {
            if (preventDeleteImageFile) {
                // set SQL_COL_PATH empty so sql-delete cannot cascade delete the referenced image-file via delete trigger
                ContentValues values = new ContentValues();
                values.put(FotoSql.SQL_COL_PATH, DELETED_FILE_MARKER);
                values.put(FotoSql.SQL_COL_EXT_MEDIA_TYPE, 0); // so it will not be shown as image any more
                exexUpdateImpl(dbgContext + "-a: FotoSql.deleteMedia: ",
                        context, values, lastUsedWhereClause, lastSelectionArgs);

                lastUsedWhereClause = FotoSql.SQL_COL_PATH + " is null";
                lastSelectionArgs = null;
                delCount = context.getContentResolver()
                        .delete(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE, lastUsedWhereClause, lastSelectionArgs);
                if (Global.debugEnabledSql || LibGlobal.debugEnabledJpg) {
                    Log.i(Global.LOG_CONTEXT, dbgContext + "-b: FotoSql.deleteMedia delete\n" +
                            QueryParameter.toString(null, null, SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME,
                            lastUsedWhereClause, lastSelectionArgs, null, delCount));
                }
            } else {
                delCount = context.getContentResolver()
                        .delete(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE, lastUsedWhereClause, lastSelectionArgs);
                if (Global.debugEnabledSql || LibGlobal.debugEnabledJpg) {
                    Log.i(Global.LOG_CONTEXT, dbgContext +": FotoSql.deleteMedia\ndelete " +
                            QueryParameter.toString(null, null,
                                    SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME,
                                    lastUsedWhereClause, lastSelectionArgs, null, delCount));
                }
            }
        } catch (Exception ex) {
            // null pointer exception when delete matches not items??
            final String msg = dbgContext + ": Exception in FotoSql.deleteMedia:\n" +
                    QueryParameter.toString(null, null, SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME,
                    lastUsedWhereClause, lastSelectionArgs, null, -1)
                    + " : " + ex.getMessage();
            Log.e(Global.LOG_CONTEXT, msg, ex);

        }
        return delCount;
    }

    /** converts imageID to content-uri */
    public static Uri getUri(long imageID) {
        return Uri.parse(
                getUriString(imageID));
    }

    /** get imageID from content-url or null if not found */
    public static Long getId(Uri uriWithId) {
        Long imageID = null;
        String idString = (uriWithId == null) ? null : uriWithId.getLastPathSegment();
        if (idString != null) {
            try {
                imageID = (idString == null) ? null : Long.valueOf(idString);
            } catch (NumberFormatException e) {
                Log.e(Global.LOG_CONTEXT, "FotoSql.getId(" + uriWithId + ") => " + e.getMessage());
            }
        }
        return imageID;
    }

    public static void addDateAdded(ContentValues values) {
        long now = new Date().getTime();
        values.put(SQL_COL_DATE_ADDED, now / LAST_MODIFIED_FACTOR);//sec
    }

    @NonNull
    public static String getUriString(long imageID) {
        return SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME + "/" + imageID;
    }

    public static SelectedFiles getSelectedfiles(Context context, String sqlWhere, VISIBILITY visibility) {
        QueryParameter query = new QueryParameter(FotoSql.queryChangePath);
        query.addWhere(sqlWhere);
        query.addOrderBy(FotoSql.SQL_COL_PATH);

        return getSelectedfiles(context, query, FotoSql.SQL_COL_PATH, visibility);

    }

    @Nullable
    public static String getMinFolder(Context context, QueryParameter query,
                                      boolean removeLastModifiedFromFilter) {
        QueryParameter queryModified = new QueryParameter(query);
        queryModified.clearColumns().addColumn("min(" + SQL_EXPR_FOLDER + ")");

        if (removeLastModifiedFromFilter) {
            parseDateModifiedMin(queryModified, true);
            parseDateModifiedMax(queryModified, true);
        }

        Cursor c = null;

        try {
            c = FotoSql.createCursorForQuery(null, "getCount", context, queryModified, null);
            if (c.moveToNext()) {
                return c.getString(0);
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.getMinFolder() error :", ex);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    @Nullable
    public static long getCount(Context context, QueryParameter query) {
        QueryParameter queryModified = new QueryParameter(query);
        queryModified.clearColumns().addColumn("count(*)");
        Cursor c = null;

        try {
            c = FotoSql.createCursorForQuery(null, "getCount", context, queryModified, null);
            if (c.moveToNext()) {
                return c.getLong(0);
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.getCount() error :", ex);
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    /**
     * get display string with count and total size
     * @return {getString(prefixStringId)}: #{count(*)} / {sum(size)} Mb
     */
    @Nullable
    public static CharSequence getStatisticsMessage(Context context, int prefixStringId, QueryParameter query) {
        if (query == null) return null;
        String text = context.getString(prefixStringId);

        QueryParameter queryModified = new QueryParameter(query);
        queryModified
                .clearColumns()
                .addColumn("count(*)")
                .addColumn("sum(" + FotoSql.SQL_COL_SIZE + ")");
        Cursor c = null;

        try {
            c = FotoSql.createCursorForQuery(null, "getCount", context, queryModified, null);
            if (c.moveToNext()) {
                final long count = c.getLong(0);
                long size = c.getLong(1);
                String unit = "b";
                if (size > SIZE_TRANLATION_LIMIT) {
                    size /= SIZE_K;
                    unit = "kb";
                    if (size > SIZE_TRANLATION_LIMIT) {
                        size /= SIZE_K;
                        unit = "Mb";
                    }
                }
                return StringUtils.appendMessage(null,
                        text,
                        ": #",
                        count,
                        "/",
                        size,
                        unit
                );
            }
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.getStatisticsMessage() error :", ex);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    @Nullable
    private static SelectedFiles getSelectedfiles(Context context, QueryParameter query, String colnameForPath, VISIBILITY visibility) {
        SelectedFiles result = null;
        Cursor c = null;

        try {
            c = FotoSql.createCursorForQuery(null, "getSelectedfiles", context, query, visibility);
            int len = c.getCount();
            Long[] ids = new Long[len];
            String[] paths = new String[len];
            int pkColNo = c.getColumnIndex(FotoSql.SQL_COL_PK);
            int pathColNo = c.getColumnIndex(colnameForPath);
            int row = 0;
            while (c.moveToNext()) {
                paths[row] = c.getString(pathColNo);
                ids[row] = c.getLong(pkColNo);
                row++;
            }

            result = new SelectedFiles(paths, ids, null);
        } catch (Exception ex) {
            Log.e(Global.LOG_CONTEXT, "FotoSql.getSelectedfiles() error :", ex);
        } finally {
            if (c != null) c.close();
        }

        if (Global.debugEnabled) {
            Log.d(Global.LOG_CONTEXT, "FotoSql.getSelectedfiles result count=" + ((result != null) ? result.size():0));
        }

        return result;
    }

    public static Date getDate(Cursor cursor,int colDateTimeTaken) {
        if (colDateTimeTaken == -1) return null;
        Long value = cursor.getLong(colDateTimeTaken);
        return (value != null) ? new Date(value.longValue()) : null;
    }

    /** converts internal ID-list to string array of filenNames via media database. */
    public static List<String> getFileNames(Context context, SelectedItems items, List<Long> ids, List<String> paths, List<Date> datesPhotoTaken) {
        if (!items.isEmpty()) {
            // query ordered by DatePhotoTaken so that lower rename-numbers correspond to older images.
            QueryParameter parameters = new QueryParameter(queryAutoRename);
            setWhereSelectionPks(parameters, items);

            List<String> result = getFileNamesImpl(context, parameters, ids, paths, datesPhotoTaken);
            int size = result.size();

            if (size > 0) {
                return result;
            }
        }
        return null;

    }

    /** converts internal ID-list to string array of filenNames via media database. */
    public static String[] getFileNames(Context context, String pksAsListString , List<Long> ids, List<String> paths, List<Date> datesPhotoTaken) {
        if ((pksAsListString != null) && !pksAsListString.isEmpty()) {
            // query ordered by DatePhotoTaken so that lower rename-numbers correspond to older images.
            QueryParameter parameters = new QueryParameter(queryAutoRename);
            setWhereSelectionPks(parameters, pksAsListString);

            List<String> result = getFileNamesImpl(context, parameters, ids, paths, datesPhotoTaken);
            int size = result.size();

            if (size > 0) {
                return result.toArray(new String[size]);
            }
        }
        return null;
    }

    private static List<String> getFileNamesImpl(Context context, QueryParameter parameters, List<Long> ids, List<String> paths, List<Date> datesPhotoTaken) {
        List<String> result = (paths != null) ? paths : new ArrayList<String>();

        Cursor cursor = null;

        try {
            cursor = createCursorForQuery(null, "getFileNames", context, parameters, VISIBILITY.PRIVATE_PUBLIC);

            int colPath = cursor.getColumnIndex(SQL_COL_DISPLAY_TEXT);
            if (colPath == -1) colPath = cursor.getColumnIndex(SQL_COL_PATH);
            int colIds = (ids == null) ? -1 : cursor.getColumnIndex(SQL_COL_PK);
            int colDates = (datesPhotoTaken == null) ? -1 : cursor.getColumnIndex(SQL_COL_DATE_TAKEN);
            while (cursor.moveToNext()) {
                result.add(cursor.getString(colPath));
                if (colIds >= 0) ids.add(cursor.getLong(colIds));
                if (colDates >= 0) datesPhotoTaken.add(getDate(cursor, colDates));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    protected static String getFilterExpressionVisibility(VISIBILITY _visibility) {
        VISIBILITY visibility = _visibility;
        // add visibility column only if not included yet
        if (visibility == VISIBILITY.DEFAULT) {
            visibility = (LibGlobal.visibilityShowPrivateByDefault)
                    ? VISIBILITY.PRIVATE_PUBLIC
                    : VISIBILITY.PUBLIC;
        }

        switch (visibility) {
            case PRIVATE:
                return FILTER_EXPR_PRIVATE;
            case PRIVATE_PUBLIC:
                return FILTER_EXPR_PRIVATE_PUBLIC;
            case PUBLIC:
            default:
                return FILTER_EXPR_PUBLIC;
        }
    }

    /** adds visibility to sql of parameters, if not set yet */
    public static QueryParameter setWhereVisibility(QueryParameter parameters, VISIBILITY visibility) {
        if (parameters.toFrom() == null) {
            parameters.addFrom(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME);
        }

        if (visibility != null) {
            String sqlWhere = parameters.toAndroidWhere();
            if ((sqlWhere == null) || (parameters.toFrom().contains(SQL_TABLE_EXTERNAL_CONTENT_URI_FILE_NAME) && !sqlWhere.contains(SQL_COL_EXT_MEDIA_TYPE))) {
                parameters.addWhere(getFilterExpressionVisibility(visibility));
            }
        }

        return parameters;
    }

    public static List<String> getAlbumFiles(Context context, String path, int subDirLevels) {
        SelectedFiles databaseFiles = FotoSql.getSelectedfiles(context,
                SQL_COL_PATH +" like '" + path +  "/%" + AlbumFile.SUFFIX_VALBUM + "' OR " +
                SQL_COL_PATH +" like '" + path +  "/%" + AlbumFile.SUFFIX_QUERY + "'", null);
        String[] fileNames = (databaseFiles == null) ? null : databaseFiles.getFileNames();
        List<String> paths = new ArrayList<String>();

        // copy all items that are not deeper that subDirLevels
        int numSegs = StringUtils.charCount(path,'/') + subDirLevels;
        if (fileNames != null) {
            for (int i = fileNames.length - 1; i >= 0; i--) {
                String fileName = fileNames[i];
                if ((fileName != null) && (StringUtils.charCount(fileName, '/') <= numSegs)) {
                    paths.add(fileName);
                }
            }
        }
        return paths;
    }

    public static int execRename(Context context, String oldFullPath, String newFullPath) {
        ContentValues values = new ContentValues();
        values.put(SQL_COL_PATH, newFullPath);
        return FotoSql.execUpdate("rename file", context, oldFullPath,
                values, null);
    }

    public static class CursorLoaderWithException extends CursorLoader {
        private final QueryParameter query;
        private Exception mException;

        public CursorLoaderWithException(Context context, QueryParameter query) {
            super(context, Uri.parse(query.toFrom()), query.toColumns(), query.toAndroidWhere(), query.toAndroidParameters(), query.toOrderBy());
            this.query = query;
        }

        @Override
        public Cursor loadInBackground() {
            mException = null;
            try {
                Cursor result = super.loadInBackground();
                return result;
            } catch (Exception ex) {
                final String msg = "FotoSql.createCursorLoader()#loadInBackground failed:\n\t" + query.toSqlString();
                Log.e(Global.LOG_CONTEXT, msg, ex);
                mException = ex;
                return null;
            }
        }

        public QueryParameter getQuery() {
            return query;
        }

        public Exception getException() {
            return mException;
        }
    }
}


