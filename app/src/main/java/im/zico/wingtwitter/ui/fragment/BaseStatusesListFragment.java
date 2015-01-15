package im.zico.wingtwitter.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Toast;

import com.nineoldandroids.view.ViewPropertyAnimator;
import com.tjerkw.slideexpandable.library.ActionSlideExpandableListView;

import java.util.ArrayList;

import im.zico.wingtwitter.R;
import im.zico.wingtwitter.WingApp;
import im.zico.wingtwitter.adapter.TimeLineAdapter;
import im.zico.wingtwitter.dao.WingDataHelper;
import im.zico.wingtwitter.dao.WingStore;
import im.zico.wingtwitter.type.WingTweet;
import im.zico.wingtwitter.ui.TweetComposeActivity;
import im.zico.wingtwitter.ui.view.LoadingFooter;
import im.zico.wingtwitter.ui.view.TweetListView;
import im.zico.wingtwitter.utils.PreferencesManager;
import im.zico.wingtwitter.utils.TweetUtils;
import im.zico.wingtwitter.utils.WConfig;
import twitter4j.AsyncTwitter;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterListener;
import twitter4j.TwitterMethod;

/**
 * Created by tinyao on 12/4/14.
 */
public abstract class BaseStatusesListFragment extends BaseFragment implements LoaderManager.LoaderCallbacks<Cursor>,
        SwipeRefreshLayout.OnRefreshListener, TweetListView.ScrollDetectCallback {

    private static final int TASK_TIMELINE = 1;
    private static final int TASK_FAVORITE = 2;
    private static final int TASK_UNFAVORITE = 3;
    private static final int TASK_FAVORITE_TIMELINE = 4;
    private static final int TASK_EXCPTION = -1;
    public SwipeRefreshLayout mSwipeRefresh;
    TweetListView mListView;
    TimeLineAdapter mAdapter;
    int mPageId = 0;

    private AsyncTwitter mAsyncTwitter;
    private LoadingFooter mLoadingFooter;
    private WingDataHelper DBHelper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_blank, container, false);
        mListView = (TweetListView) rootView.findViewById(R.id.list);
        mLoadingFooter = new LoadingFooter(getActivity());
        mListView.addFooterView(mLoadingFooter.getView());
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getAsyncTwitter().addListener(listener);
        bindSwipeToRefresh((ViewGroup) view);

        DBHelper = new WingDataHelper(getActivity());
        mAdapter = new TimeLineAdapter(getActivity(), mListView);
        mListView.setAdapter(mAdapter);
        getLoaderManager().initLoader(0, null, this);

        mListView.setScrollCallback(this);

        mListView.setItemActionListener(new ActionSlideExpandableListView.OnActionClickListener() {
            @Override
            public void onClick(final View itemView, View clickedView, final int position) {
                final WingTweet tweet = mAdapter.getItem(position);
                switch (clickedView.getId()) {
                    case R.id.expand_action_reply:
                        Bundle bundle = new Bundle();
                        bundle.putString("user", "@" + tweet.screen_name);
                        bundle.putLong("inReplyId", tweet.tweet_id);
                        TweetComposeActivity.showDialog(getActivity(), bundle);
                        break;
                    case R.id.expand_action_share:
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT,
                                "@" + tweet.screen_name + ": " + tweet.content);
                        sendIntent.setType("text/plain");
                        startActivity(sendIntent);
                        break;
                    case R.id.expand_action_favorite:
                        WingTweet favTweet = mAdapter.getItem(position);
                        if (!favTweet.favorited) {
                            getAsyncTwitter().createFavorite(tweet.tweet_id);
                        } else {
                            if (getType() == WingStore.TYPE_FAVORITE) {
                                ViewPropertyAnimator.animate(itemView).setInterpolator(new AccelerateDecelerateInterpolator())
                                        .setDuration(200)
                                        .translationXBy(0-getResources().getDisplayMetrics().widthPixels)
                                        .setListener(new com.nineoldandroids.animation.AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(com.nineoldandroids.animation.Animator animation) {
                                                super.onAnimationEnd(animation);
                                                getDBHelper().delete(mAdapter.getItem(position), WingStore.TYPE_FAVORITE);
                                                getAsyncTwitter().destroyFavorite(tweet.tweet_id);
                                                performDismiss(itemView);
                                            }
                                        });

                            } else {
                                getAsyncTwitter().destroyFavorite(tweet.tweet_id);
                            }
                        }
                        break;
                    case R.id.expand_action_retweet:
                        new AlertDialog.Builder(getActivity())
                                .setMessage("Retweet or Quote ?")
                                .setNeutralButton(R.string.cancel, null)
                                .setPositiveButton(R.string.retweet, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        getAsyncTwitter().retweetStatus(tweet.tweet_id);
                                    }
                                })
                                .setNegativeButton(R.string.quote, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Bundle bnd = new Bundle();
                                        bnd.putString("quote", "RT " + tweet.content);
                                        TweetComposeActivity.showDialog(getActivity(), bnd);
                                    }
                                }).create().show();
                        break;
                    case R.id.expand_action_copy:
                        ClipboardManager clipboard = (ClipboardManager)
                                getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("wing", tweet.content);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getActivity(), "Copyed to clipboard !", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.expand_action_delete:
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Delete")
                                .setMessage("Do you want to delete this Tweet?")
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        getDBHelper().delete(tweet);
                                        getAsyncTwitter().destroyStatus(tweet.tweet_id);
                                    }
                                })
                                .setNegativeButton(R.string.no, null)
                                .create().show();
                        break;
                    case R.id.expand_action_filter:
                        // ToDo: Filter user or source
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Add to filter")
                                .setMessage("User: " + tweet.user_name + "\nSoure: " + tweet.source)
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                })
                                .setNegativeButton(R.string.no, null)
                                .create().show();
                        break;
                }

                mListView.collapse();
            }
        });

    }

    @Override
    public void onScrollFooter() {
        if (mAdapter.getCount() == 0){
            return;
        }
        if (mLoadingFooter.getState() != LoadingFooter.State.Loading
                && mLoadingFooter.getState() != LoadingFooter.State.TheEnd) {
            mLoadingFooter.setState(LoadingFooter.State.Loading);
            loadNext();
        }
    }

    @Override
    public void onScrollDown() {

    }

    @Override
    public void onScrollUp() {

    }

    public void scrollTop() {
        mListView.smoothScrollToPositionFromTop(0, 0);
    }

    /**
     * Bind a SwipeRefreshLayout to the contentView
     * @param v
     */
    protected void bindSwipeToRefresh(ViewGroup v) {
        mSwipeRefresh = new SwipeRefreshLayout(getActivity());
        // Move child to SwipeRefreshLayout, and add SwipeRefreshLayout to root view
        v.removeViewInLayout(mListView);
        v.addView(mSwipeRefresh, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mSwipeRefresh.addView(mListView, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        mSwipeRefresh.setColorSchemeResources(
                R.color.refresh_progress_1,
                R.color.refresh_progress_2,
                R.color.refresh_progress_3);
        mSwipeRefresh.setOnRefreshListener(this);
    }

    public WingDataHelper getDBHelper() {
        return DBHelper;
    }

    /**
     * Impliment to provide the main status type in fragment,
     * such as Tweet, Mentioned, Favorite
     * @return
     */
    public abstract int getType();

    /**
     * Create a AsyncTwitter Instance for each fragment instance
     * @return AsyncTwitter
     */
    public AsyncTwitter getAsyncTwitter(){
        if (mAsyncTwitter == null) {
            mAsyncTwitter = WingApp.newTwitterInstance();
        }
        return mAsyncTwitter;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return DBHelper.getCursorLoader(getType());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        if (isListEmpty() && data.getCount() > WConfig.TIMELINE_MAX_CACHE) {
            // There are over 200 tweets cached in database, clear at each launch time
            // isListEmpty means just launched
            Log.d("DEBUG", "remove old");
            DBHelper.clearOldTweets(getType());
        }

        if (mAdapter.getCount() > 0 && mLoadingFooter.getState() != LoadingFooter.State.Loading) {
            int firstVisPos = mListView.getFirstVisiblePosition();
            View firstVisView = mListView.getChildAt(0);
            int top = firstVisView != null ? firstVisView.getTop() : 0;
            mListView.setBlockLayoutChildren(true);
            int itemsAddedBeforeFirstVisible = data.getCount() - mAdapter.getCount();
            mAdapter.changeCursor(data);
            mListView.setBlockLayoutChildren(false);
            mListView.setSelectionFromTop(firstVisPos + itemsAddedBeforeFirstVisible, top);
        } else {
            mAdapter.changeCursor(data);
        }

        // If there isn't any cache, load from the server
        if (data != null && data.getCount() == 0) {
            loadLatest();
        }
    }

    private void performDismiss(final View mDismissView) {
        // Animate the dismissed view to zero-height and then fire the dismiss callback.
        // This triggers layout on each animation frame; in the future we may want to do something
        // smarter and more performant.

        final ViewGroup.LayoutParams lp = mDismissView.getLayoutParams();
        final int originalHeight = mDismissView.getHeight();

        ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(200);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset view presentation
                mDismissView.setAlpha(1f);
                mDismissView.setTranslationX(0);
                lp.height = originalHeight;
                mDismissView.setLayoutParams(lp);
            }
        });

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                lp.height = (Integer) valueAnimator.getAnimatedValue();
                mDismissView.setLayoutParams(lp);
            }
        });

        animator.start();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }

    @Override
    public void onRefresh() {
        loadLatest();
    }

    /**
     * When pull to refresh, request for latest tweets
     */
    public abstract void loadLatest();

    /**
     * When arrive at the footer, request for older tweets
     */
    public abstract void loadNext();

    public boolean isListEmpty() {
        return mAdapter == null || mAdapter.getCount() == 0;
    }

    /**
     * Listener for TwitterMethod Callback
     */
    private TwitterListener listener = new TwitterAdapter() {
        @Override
        public void gotHomeTimeline(ResponseList<Status> statuses) {
            super.gotHomeTimeline(statuses);
            onTimelineResult(statuses);
        }

        @Override
        public void gotMentions(ResponseList<Status> statuses) {
            super.gotMentions(statuses);
            onTimelineResult(statuses);
        }

        @Override
        public void gotFavorites(ResponseList<Status> statuses) {
            super.gotFavorites(statuses);
            onTimelineResult(statuses);
        }

        @Override
        public void createdFavorite(Status status) {
            WingTweet favTweet = getDBHelper().getTweet(status.getId(), getType());
            favTweet.favorited = true;
            getDBHelper().update(favTweet);
            getDBHelper().save(favTweet, WingStore.TYPE_FAVORITE);
        }

        @Override
        public void destroyedFavorite(Status status) {
            WingTweet unfavTweet = getDBHelper().getTweet(status.getId(), getType());
            unfavTweet.favorited = false;
            getDBHelper().update(unfavTweet);
            getDBHelper().delete(unfavTweet, WingStore.TYPE_FAVORITE);
        }

        @Override
        public void onException(TwitterException te, TwitterMethod method) {
            super.onException(te, method);
            onTwitterError(te, method);
        }
    };

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        Log.d("DEBUG", "onViewStateRestored");
        if(savedInstanceState!=null) {
            mListView.setSelectionFromTop(savedInstanceState.getInt("index"), savedInstanceState.getInt("top"));
        }
    }

    public void onTimelineResult(ResponseList<Status> statuses) {
        Message msg = mHandler.obtainMessage();
        msg.obj = statuses;
        msg.what = TASK_TIMELINE;
        mHandler.sendMessage(msg);
    }

    public void onTwitterError(TwitterException te, TwitterMethod method) {
        String error2Show = TweetUtils.parseException(te, method);
        Message msg = new Message();
        msg.obj = error2Show;
        msg.arg1 = method.ordinal();
        msg.what = TASK_EXCPTION;
        mHandler.sendMessage(msg);
    }

    /**
     *
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TASK_EXCPTION:
                    if (msg.arg1 == TwitterMethod.HOME_TIMELINE.ordinal()) {
                        mSwipeRefresh.setRefreshing(false);
                        Toast.makeText(getActivity(), msg.obj + "", Toast.LENGTH_SHORT).show();
                    } else if (msg.arg1 == TwitterMethod.CREATE_FAVORITE.ordinal()) {
                        Toast.makeText(getActivity(), "Fail to favorite", Toast.LENGTH_SHORT).show();
                    } else if (msg.arg1 == TwitterMethod.DESTROY_FAVORITE.ordinal()) {
                        Toast.makeText(getActivity(), "Fail to delete favorite", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case TASK_TIMELINE:
                    ResponseList<Status> statuses = (ResponseList<Status>) msg.obj;
                    if (!mSwipeRefresh.isRefreshing()) {
                        // load old
                        if (statuses == null || statuses.size() < 10) {
                            Log.d("DEBUG", "the end");
                            mLoadingFooter.setState(LoadingFooter.State.TheEnd);
                        } else {
                            mLoadingFooter.setState(LoadingFooter.State.Idle, 3000);
                        }
                    } else {
                        // load latest
                        if (statuses.size() > 0) {
                            Toast.makeText(getActivity(), statuses.size() + " New Tweets",
                                    Toast.LENGTH_SHORT).show();
                        }
                        mSwipeRefresh.setRefreshing(false);
                        if (statuses.size() >= 17) {
                            DBHelper.deleteAllTweets();
                        }
                    }

                    ArrayList<WingTweet> wingTweets = new ArrayList<>();
                    for (Status ss : statuses) {
                        WingTweet tweet = new WingTweet(ss);
                        wingTweets.add(tweet);
                    }
                    getDBHelper().saveAll(wingTweets, getType());
                    break;
            }
            super.handleMessage(msg);
        }
    };

}
