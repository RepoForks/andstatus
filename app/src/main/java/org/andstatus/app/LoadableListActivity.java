/* 
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.ListView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.widget.MyBaseAdapter;

/**
 * List, loaded asynchronously. Updated by MyService
 * 
 * @author yvolk@yurivolkov.com
 */
public abstract class LoadableListActivity extends MyBaseListActivity implements MyServiceEventsListener {

    ParsedUri mParsedUri = ParsedUri.fromUri(Uri.EMPTY);

    /**
     * We use this to request additional items (from Internet)
     */
    private MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    private long mInstanceId;
    MyServiceEventsReceiver myServiceReceiver;

    private final Object loaderLock = new Object();
    @GuardedBy("loaderLock")
    private AsyncLoader mCompletedLoader = new AsyncLoader();
    @GuardedBy("loaderLock")
    private AsyncLoader mWorkingLoader = mCompletedLoader;
    @GuardedBy("loaderLock")
    private boolean loaderIsWorking = false;

    private boolean mIsPaused = false;

    protected CharSequence mSubtitle = "";
    /**
     * Id of current list item, which is sort of a "center" of the list view
     */
    protected long centralItemId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mInstanceId == 0) {
            mInstanceId = InstanceId.next();
            MyLog.v(this, "onCreate instanceId=" + mInstanceId);
        } else {
            MyLog.v(this, "onCreate reuse the same instanceId=" + mInstanceId);
        }
        MyServiceManager.setServiceAvailable();
        myServiceReceiver = new MyServiceEventsReceiver(this);

        mParsedUri = ParsedUri.fromUri(getIntent().getData());
        ma = MyContextHolder.get().persistentAccounts().fromUserId(getParsedUri().getAccountUserId());
        centralItemId = getParsedUri().getItemId();
    }

    protected ParsedUri getParsedUri() {
        return mParsedUri;
    }

    protected void showList(WhichPage whichPage) {
        showList(whichPage.toBundle());
    }

    protected void showList(Bundle args) {
        String msgLog = "showList, instanceId=" + mInstanceId
                + ", " + WhichPage.load(args) + " page"
                + (centralItemId == 0 ? "" : ", center:" + centralItemId);
        MyLog.v(this, "Started " + msgLog);
        synchronized (loaderLock) {
            if (loaderIsWorking && mWorkingLoader.getStatus() == Status.FINISHED) {
                msgLog += ", " + mWorkingLoader;
                loaderIsWorking = false;
            }
            if (loaderIsWorking) {
                msgLog = "Ignored " + msgLog + ", " + mWorkingLoader;
            } else {
                mWorkingLoader = new AsyncLoader();
                loaderIsWorking = true;
                mWorkingLoader.execute(args);
            }
        }
        MyLog.v(this, "Ended " + msgLog);
    }

    public boolean isLoading() {
        synchronized (loaderLock) {
            return loaderIsWorking;
        }
    }

    public interface SyncLoader {
        void allowLoadingFromInternet();
        void load(ProgressPublisher publisher);
        int size();
    }
    
    public interface ProgressPublisher {
        void publish(String progress);
    }
    
    protected abstract SyncLoader newSyncLoader(Bundle args);
    
    private class AsyncLoader extends AsyncTask<Bundle, String, SyncLoader> implements LoadableListActivity.ProgressPublisher {
        private volatile long timeStarted = 0;
        private volatile long timeLoaded = 0;
        private volatile long timeCompleted = 0;
        private SyncLoader mSyncLoader = null;

        SyncLoader getSyncLoader() {
            return mSyncLoader == null ? newSyncLoader(null) : mSyncLoader;
        }

        @Override
        protected SyncLoader doInBackground(Bundle... params) {
            timeStarted = System.currentTimeMillis();
            publishProgress("...");
            SyncLoader loader = newSyncLoader(params[0]);
            if (ma.isValidAndSucceeded()) {
                loader.allowLoadingFromInternet();
            }
            loader.load(this);
            timeLoaded = System.currentTimeMillis();
            return loader;
        }

        @Override
        public void publish(String progress) {
            publishProgress(progress);
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            updateTitle(values[0]);
        }

        @Override
        protected void onCancelled(SyncLoader syncLoader) {
            onEnd();
        }

        private void onEnd() {
            synchronized (loaderLock) {
                if (mWorkingLoader == this) {
                    loaderIsWorking = false;
                }
            }
        }

        @Override
        protected void onPostExecute(SyncLoader loader) {
            try {
                if (!mIsPaused) {
                    mSyncLoader = loader;
                    updateCompletedLoader();
                    onLoadFinished(true);
                }
            } catch (Exception e) {
                MyLog.i(this,"on Recreating view", e);
            }
            timeCompleted = System.currentTimeMillis();
            long timeTotal = timeCompleted - timeStarted;
            MyLog.v(this, "Async load completed, "
                    + (mSyncLoader == null ? "?" : mSyncLoader.size()) + " items, "
                    + timeTotal + "ms total, "
            + (timeCompleted - timeLoaded) + "ms in the foreground");
            onEnd();
        }

        @Override
        public String toString() {
            return MyLog.objTagToString(this) + "; " + this.getStatus()
                    + ", " + (mSyncLoader == null ? "" : mSyncLoader);
        }
    }

    public void onLoadFinished(boolean restorePosition) {
        updateTitle("");
        if (restorePosition) {
            ListView list = getListView();
            // TODO: for a finer position restore see http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview?rq=1
            long itemIdOfListPosition = centralItemId;
            if (list.getChildCount() > 1) {
                itemIdOfListPosition = list.getAdapter().getItemId(list.getFirstVisiblePosition());
            }
            setListAdapter(newListAdapter());
            int firstListPosition = getListAdapter().getPositionById(itemIdOfListPosition);
            if (firstListPosition >= 0) {
                list.setSelectionFromTop(firstListPosition, 0);
                getListAdapter().setPositionRestored(true);
            }
        } else {
            setListAdapter(newListAdapter());
        }
    }

    protected abstract MyBaseAdapter newListAdapter();

    @Override
    public MyBaseAdapter getListAdapter() {
        return (MyBaseAdapter) super.getListAdapter();
    }

    private void updateCompletedLoader() {
        synchronized(loaderLock) {
            mCompletedLoader = mWorkingLoader;
        }
    }
    
    protected void updateTitle(String progress) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        StringBuilder title = new StringBuilder(getCustomTitle());
        if (ma.isValid()) {
            I18n.appendWithSpace(title, "/ " + ma.getOrigin().getName());
        } else if (centralItemId != 0) {
            I18n.appendWithSpace(title, "/ ? (" + centralItemId + ")");
        }
        if (!TextUtils.isEmpty(progress)) {
            I18n.appendWithSpace(title, progress);
        }
        actionBar.setTitle(title.toString());
        actionBar.setSubtitle(mSubtitle);
    }

    protected CharSequence getCustomTitle() {
        return getTitle();
    }
    
    protected int size() {
        synchronized(loaderLock) {
            return getLoaded().size();
        }
    }

    protected SyncLoader getLoaded() {
        synchronized(loaderLock) {
            return mCompletedLoader.getSyncLoader();
        }
    }
    
    @Override
    protected void onResume() {
        mIsPaused = false;
        super.onResume();
        myServiceReceiver.registerReceiver(this);
        MyContextHolder.get().setInForeground(true);
        if (size() == 0) {
            showList(WhichPage.SAME);
        }
    }

    @Override
    protected void onPause() {
        mIsPaused = true;
        super.onPause();
        myServiceReceiver.unregisterReceiver(this);
        MyContextHolder.get().setInForeground(false);
    }
    
    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        if (event == MyServiceEvent.AFTER_EXECUTING_COMMAND) {
            onReceiveAfterExecutingCommand(commandData);
        }
    }

    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        switch(commandData.getCommand()) {
            case GET_STATUS:
            case GET_USER:
            case UPDATE_STATUS:
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case REBLOG:
            case DESTROY_REBLOG:
            case DESTROY_STATUS:
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
                if (!commandData.getResult().hasError()) {
                    showList(WhichPage.YOUNGEST);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_menu_item:
                showList(WhichPage.YOUNGEST);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public MyAccount getMa() {
        return ma;
    }

    public boolean isPositionRestored() {
        return getListAdapter() != null && getListAdapter().isPositionRestored();
    }
}
