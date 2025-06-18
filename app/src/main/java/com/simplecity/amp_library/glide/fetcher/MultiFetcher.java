package com.simplecity.amp_library.glide.fetcher;

import android.content.Context;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.simplecity.amp_library.ShuttleApplication;
import com.simplecity.amp_library.model.ArtworkProvider;
import com.simplecity.amp_library.model.UserSelectedArtwork;
import com.simplecity.amp_library.utils.SettingsManager;
import com.simplecity.amp_library.utils.ShuttleUtils;
import java.io.File;
import java.io.InputStream;

public class MultiFetcher implements DataFetcher<InputStream> {

    private static final String TAG = "MultiFetcher";

    private Context applicationContext;

    private DataFetcher<InputStream> dataFetcher;

    private ArtworkProvider artworkProvider;

    private SettingsManager settingsManager;

    private boolean allowOfflineDownload = false;

    public MultiFetcher(Context context, ArtworkProvider artworkProvider, SettingsManager settingsManager, boolean allowOfflineDownload) {
        applicationContext = context;
        this.artworkProvider = artworkProvider;
        this.settingsManager = settingsManager;
        this.allowOfflineDownload = allowOfflineDownload;
    }

    private InputStream loadData(DataFetcher<InputStream> dataFetcher, Priority priority) {
        InputStream inputStream;
        try {
            inputStream = dataFetcher.loadData(priority);
        } catch (Exception e) {
            if (dataFetcher != null) {
                dataFetcher.cleanup();
            }
            inputStream = null;
        }
        return inputStream;
    }

    @Override
    public InputStream loadData(Priority priority) throws Exception {
        InputStream inputStream = loadUserSelectedArtwork(priority);
        if (inputStream == null && !settingsManager.ignoreMediaStoreArtwork()) {
            inputStream = tryLoad(new MediaStoreFetcher(applicationContext, artworkProvider), priority);
        }

        if (inputStream == null) {
            inputStream = settingsManager.preferEmbeddedArtwork()
                    ? loadEmbeddedFirst(priority)
                    : loadFolderFirst(priority);
        }

        if (inputStream == null && (allowOfflineDownload || (settingsManager.canDownloadArtworkAutomatically()
                && ShuttleUtils.isOnline(applicationContext, true)))) {
            inputStream = tryLoad(new RemoteFetcher(artworkProvider), priority);
        }

        return inputStream;
    }

    private InputStream loadUserSelectedArtwork(Priority priority) throws Exception {
        UserSelectedArtwork artwork = ((ShuttleApplication) applicationContext).userSelectedArtwork.get(artworkProvider.getArtworkKey());
        if (artwork == null) return null;

        switch (artwork.type) {
            case ArtworkProvider.Type.MEDIA_STORE:
                dataFetcher = new MediaStoreFetcher(applicationContext, artworkProvider);
                break;
            case ArtworkProvider.Type.FOLDER:
                dataFetcher = new FolderFetcher(artworkProvider, new File(artwork.path));
                break;
            case ArtworkProvider.Type.TAG:
                dataFetcher = new TagFetcher(artworkProvider);
                break;
            case ArtworkProvider.Type.REMOTE:
                dataFetcher = new RemoteFetcher(artworkProvider);
                break;
            default:
                return null;
        }
        return loadData(dataFetcher, priority);
    }

    private InputStream loadEmbeddedFirst(Priority priority) throws Exception {
        if (!settingsManager.ignoreEmbeddedArtwork()) {
            InputStream input = tryLoad(new TagFetcher(artworkProvider), priority);
            if (input != null) return input;
        }

        if (!settingsManager.ignoreFolderArtwork()) {
            return tryLoad(new FolderFetcher(artworkProvider, null), priority);
        }

        return null;
    }

    private InputStream loadFolderFirst(Priority priority) throws Exception {
        if (!settingsManager.ignoreFolderArtwork()) {
            InputStream input = tryLoad(new FolderFetcher(artworkProvider, null), priority);
            if (input != null) return input;
        }

        if (!settingsManager.ignoreEmbeddedArtwork()) {
            return tryLoad(new TagFetcher(artworkProvider), priority);
        }

        return null;
    }

    private InputStream tryLoad(DataFetcher fetcher, Priority priority) {
        dataFetcher = fetcher;
        return loadData(fetcher, priority);
    }


    @Override
    public void cleanup() {
        if (dataFetcher != null) {
            dataFetcher.cleanup();
        }
    }

    @Override
    public void cancel() {
        if (dataFetcher != null) {
            dataFetcher.cancel();
        }
    }

    private String getCustomArtworkSuffix(Context context) {
        if (((ShuttleApplication) context.getApplicationContext()).userSelectedArtwork.containsKey(artworkProvider.getArtworkKey())) {
            UserSelectedArtwork userSelectedArtwork = ((ShuttleApplication) context.getApplicationContext()).userSelectedArtwork.get(artworkProvider.getArtworkKey());
            return "_" + userSelectedArtwork.type + "_" + (userSelectedArtwork.path == null ? "" : userSelectedArtwork.path.hashCode());
        }
        return "";
    }

    @Override
    public String getId() {
        return artworkProvider.getArtworkKey() + getCustomArtworkSuffix(applicationContext);
    }
}
