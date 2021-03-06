package com.moggot.vkontaktephotoviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vk.sdk.api.model.VKApiPhoto;
import com.vk.sdk.api.model.VKPhotoArray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.vk.sdk.VKUIHelper.getApplicationContext;

public class PreviewFragment extends Fragment {

    private static final String LOG_TAG = PreviewFragment.class.getSimpleName();

    private PreviewAdapter adapter;
    private List<Bitmap> photosBitmap;
    private VKPhotoArray photos;
    private DownloadImageSetTask task;

    public PreviewFragment() {
    }

    public static PreviewFragment newInstance() {
        return new PreviewFragment();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (!isNetworkAvailable()) {
            internetUnavailable();
            return;
        }

        photosBitmap = new ArrayList<>();
        adapter = new PreviewAdapter(photosBitmap);

        VKAccessToken token = VKAccessToken.currentToken();
        VKRequest request = new VKRequest("photos.getAll", VKParameters.from(token.userId, "request", "extended", "1", "count", "200"), VKPhotoArray.class);
        request.executeWithListener(new VKRequest.VKRequestListener() {
            @Override
            public void onComplete(VKResponse response) {
                try {
                    photos = (VKPhotoArray) response.parsedModel;
                    task = (DownloadImageSetTask) getActivity().getLastNonConfigurationInstance();
                    if (task == null) {
                        task = new DownloadImageSetTask();
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, photos);
                    }
                    task.link(PreviewFragment.this);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.imageGallery);
        recyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new StaggeredGridLayoutManager(3, 1);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);


        recyclerView.addOnItemTouchListener(new PreviewAdapter.RecyclerTouchListener(getApplicationContext(), new PreviewAdapter.ClickListener() {
            @Override
            public void onClick(View view, int position) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Consts.EXTRA_PHOTO_ARRAY, photos);
                bundle.putInt(Consts.EXTRA_PHOTO_POSITION, position);

                FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
                SlideshowDialogFragment newFragment = SlideshowDialogFragment.newInstance();
                newFragment.setArguments(bundle);
                newFragment.show(ft, "slideshow");
            }

        }));
    }

    private Object onRetainNonConfigurationInstance() {
        task.unLink();
        return task;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                VKSdk.logout();
                if (VKSdk.isLoggedIn())
                    Log.v(LOG_TAG, "CAN'T logout");
                else
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, LoginFragment.newInstance())
                            .commitAllowingStateLoss();
                return true;

        }
        return onOptionsItemSelected(item);
    }


    private static class DownloadImageSetTask extends AsyncTask<VKPhotoArray, Bitmap, Void> {

        private static final String LOG_TAG = "DownloadImageSetTask";

        private PreviewFragment fragment;

        void link(PreviewFragment fragment) {
            this.fragment = fragment;
        }

        // обнуляем ссылку
        void unLink() {
            fragment = null;
        }

        public DownloadImageSetTask() {
        }

        protected Void doInBackground(VKPhotoArray... photos) {
            for (VKApiPhoto photo : photos[0]) {
                String url = photo.photo_130;
                try {
                    InputStream in = new java.net.URL(url).openStream();
                    Bitmap image = BitmapFactory.decodeStream(in);
                    publishProgress(image);
                } catch (Exception e) {
                    Log.e("Error", e.getMessage());
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Bitmap... values) {
            super.onProgressUpdate(values);
            fragment.photosBitmap.add(values[0]);
            fragment.adapter.notifyDataSetChanged();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return (activeNetworkInfo != null && activeNetworkInfo.isConnected());
    }

    private void internetUnavailable() {
        Toast.makeText(getActivity(),
                R.string.no_internet_connection,
                Toast.LENGTH_SHORT).show();
    }
}
