package de.k3b.android.fotoviewer.gallery.cursor;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import de.k3b.android.fotoviewer.Global;
import de.k3b.android.fotoviewer.directory.DirectoryGui;
import de.k3b.android.fotoviewer.queries.FotoViewerParameter;
import de.k3b.android.fotoviewer.queries.QueryParameterParcelable;
import de.k3b.android.fotoviewer.queries.FotoSql;
import de.k3b.android.fotoviewer.R;
import de.k3b.android.fotoviewer.OnGalleryInteractionListener;
import de.k3b.android.fotoviewer.queries.Queryable;
import de.k3b.io.Directory;

/**
 * A {@link Fragment} to show ImageGallery content based on ContentProvider-Cursor.
 * Activities that contain this fragment must implement the
 * {@link OnGalleryInteractionListener} interface
 * to handle interaction events.
 * Use the {@link GalleryCursorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class GalleryCursorFragment extends Fragment  implements Queryable, DirectoryGui {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private HorizontalScrollView parentPathBarScroller;
    private LinearLayout parentPathBar;

    private HorizontalScrollView childPathBarScroller;
    private LinearLayout childPathBar;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    // for debugging
    private static int id = 1;
    private final String debugPrefix;

    private GridView galleryView;
    private GalleryCursorAdapter galleryAdapter = null;

    private OnGalleryInteractionListener mListener;
    private QueryParameterParcelable mParameters;

    /**************** construction ******************/
    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment GalleryCursorFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static GalleryCursorFragment newInstance(String param1, String param2) {
        GalleryCursorFragment fragment = new GalleryCursorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public GalleryCursorFragment() {
        debugPrefix = "GalleryCursorFragment#" + (id++)  + " ";
        // Required empty public constructor
        if (Global.debugEnabled) {
            Log.i(Global.LOG_CONTEXT, debugPrefix + "()");
        }

    }

    /**************** live-cycle ******************/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View result = inflater.inflate(R.layout.fragment_gallery, container, false);
        galleryView = (GridView) result.findViewById(R.id.gridView);

        galleryAdapter = new GalleryCursorAdapter(this.getActivity(), calculateEffectiveQueryParameters(), debugPrefix);
        // galleryAdapter.requery(this.getActivity(),calculateEffectiveQueryParameters());
        galleryView.setAdapter(galleryAdapter);

        galleryView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                onGalleryImageClick((GalleryCursorAdapter.GridCellViewHolder) v.getTag());
            }
        });

        this.parentPathBar = (LinearLayout) result.findViewById(R.id.parent_owner);
        this.parentPathBarScroller = (HorizontalScrollView) result.findViewById(R.id.parent_scroller);

        this.childPathBar = (LinearLayout) result.findViewById(R.id.child_owner);
        this.childPathBarScroller = (HorizontalScrollView) result.findViewById(R.id.child_scroller);

        return result;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnGalleryInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnGalleryInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * interface Queryable: Initiates a database requery in the background
     *
     * @param context
     * @param parameters
     */
    @Override
    public void requery(Activity context, QueryParameterParcelable parameters) {
        if (Global.debugEnabled) {
            Log.i(Global.LOG_CONTEXT, debugPrefix + "requery " + ((parameters != null) ? parameters.toSqlString() : null));
        }

        this.mParameters = parameters;

        requeryGallery();
    }

    private void requeryGallery() {
        galleryAdapter.requery(this.getActivity(), calculateEffectiveQueryParameters());
    }

    @Override
    public String toString() {
        return debugPrefix + this.galleryAdapter;
    }

    /*********************** local helper *******************************************/
    /** an Image in the FotoGallery was clicked */
    private void onGalleryImageClick(final GalleryCursorAdapter.GridCellViewHolder holder) {
        if (mListener != null) {
            QueryParameterParcelable result = this.calculateEffectiveQueryParameters();

            if (holder.filter != null) {
                FotoSql.addWhereFilter(result, holder.filter);
            }
            mListener.onGalleryImageClick(null, getUri(holder.imageID), holder.description.getText().toString(), result);
        }
    }

    /** converts imageID to content-uri */
    private Uri getUri(long imageID) {
        return Uri.parse(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/" + imageID);
    }

    /****************** path navigation *************************/

    private Directory mDirectoryRoot = null;
    private int mDirTypId = 0;
    private String mCurrentPath = null;

    /** Defines Directory Navigation */
    @Override
    public void defineDirectoryNavigation(Directory root, int dirTypId, String initialAbsolutePath) {
        mDirectoryRoot = root;
        mDirTypId = dirTypId;
        navigateTo(initialAbsolutePath);
    }

    /** Set curent selection to absolutePath */
    @Override
    public void navigateTo(String absolutePath) {
        mCurrentPath = absolutePath;
        if (mDirectoryRoot != null) {
            reload(mDirectoryRoot.find(absolutePath));
        }
        requeryGallery();
    }

    private void reload(Directory selectedChild) {
        parentPathBar.removeAllViews();
        childPathBar.removeAllViews();

        if (selectedChild != null) {

            Button first = null;
            Directory current = selectedChild;
            while (current.getParent() != null) {
                Button button = createPathButton(current);
                // add parent left to chlild
                // gui order root/../child.parent/child
                parentPathBar.addView(button, 0);
                if (first == null) first = button;
                current = current.getParent();
            }

            // scroll to right where deepest child is
            parentPathBarScroller.requestChildFocus(parentPathBar, first);

            for (Directory child : selectedChild.getChildren()) {
                Button button = createPathButton(child);
                childPathBar.addView(button);
            }
        }
    }

    private Button createPathButton(Directory currentDir) {
        Button result = new Button(getActivity());
        result.setTag(currentDir);
        result.setText(getDirectoryDisplayText(null, currentDir, (FotoViewerParameter.includeSubItems) ? Directory.OPT_SUB_ITEM : Directory.OPT_ITEM));

        result.setOnClickListener(onPathButtonClickListener);
        return result;
    }

    /** path/directory was clicked */
    private View.OnClickListener onPathButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onPathButtonClick((Directory) v.getTag());
        }
    };

    /** path/directory was clicked */
    private void onPathButtonClick(Directory newSelection) {
        if ((mListener != null) && (newSelection != null)) {
            mCurrentPath = newSelection.getAbsolute();
            mListener.onDirectoryPick(mCurrentPath, this.mDirTypId);
        }
    }

    /** getFrom tree display text */
    private static String getDirectoryDisplayText(String prefix, Directory directory, int options) {
        StringBuilder result = new StringBuilder();
        if (prefix != null) result.append(prefix);
        result.append(directory.getRelPath()).append(" ");
        Directory.appendCount(result, directory, options);
        return result.toString();
    }

    /** combine root-query plus current selected directory */
    private QueryParameterParcelable calculateEffectiveQueryParameters() {
        QueryParameterParcelable result = new QueryParameterParcelable(mParameters);

        if ((mDirTypId != 0) && (mCurrentPath != null)) {
            FotoSql.addPathWhere(result, mCurrentPath, mDirTypId);
        }
        return result;
    }
}
