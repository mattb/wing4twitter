package im.zico.wingtwitter.type;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.Html;
import android.text.util.Linkify;
import android.util.Log;
import android.util.Patterns;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.zico.wingtwitter.dao.WingStore.*;
import im.zico.wingtwitter.utils.TweetUtils;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by tinyao on 12/1/14.
 */
public class WingTweet {

    public long account_id;

    public long tweet_id;

    public long user_id;
    public String user_name;
    public String avatar_url;
    public String screen_name;

    public String content;
    public String content_html;
    public String htmlContent;
    public long created_at;
    public String source;

    public long in_reply_status_id;
    public long in_reply_user_id;
    public String in_reply_user_name;
    public String in_reply_user_screen_name;

    public boolean is_retweet;
    public long retweet_id;
    public long retweeted_by_user_id;
    public String retweeted_by_user_name;
    public String retweeted_by_user_screen_name;
    public long retweet_timestamp;

    public boolean favorited;
    public int retweet_count;
    public int favorite_count;

    public String[] mediaUrls;


    public WingTweet() {
    }

    public WingTweet(Status orig) {
        account_id = 111;
        tweet_id = orig.getId();

        is_retweet = orig.isRetweet();

        // Get the retweet status
        final Status retweeted = orig.getRetweetedStatus();
        final User retweet_user = retweeted != null ? orig.getUser() : null;
        retweet_id = retweeted != null ? retweeted.getId() : -1;
        retweet_timestamp = retweeted != null ? retweeted.getCreatedAt().getTime() : -1;
        retweeted_by_user_id = retweet_user != null ? retweet_user.getId() : -1;
        retweeted_by_user_name = retweet_user != null ? retweet_user.getName() : null;
        retweeted_by_user_screen_name = retweet_user != null ? retweet_user.getScreenName() : null;

        // If it is a retweet, main content is the retweet status
        final Status status = retweeted != null ? retweeted : orig;
        User user = status.getUser();
        user_id = user.getId();
        user_name = user.getName();
        screen_name = user.getScreenName();
        avatar_url = TweetUtils.getLargeAvatarUrl(user.getProfileImageURL());

        created_at = status.getCreatedAt().getTime();
        content = status.getText();

        content_html = content;
        URLEntity[] urlEntities = status.getURLEntities();
        for (URLEntity url : urlEntities) {
            content = content.replace(url.getText(), url.getExpandedURL());
        }
        UserMentionEntity[] mentions = status.getUserMentionEntities();
        for (UserMentionEntity mention: mentions) {
            content_html = content_html.replaceFirst("@" + mention.getText(),
                    "<a href='im.zico.wingtwitter.user://" + mention.getText() + "'>@" + mention.getText() + "</a>");
        }
        for (URLEntity url : urlEntities) {
            content_html = content_html.replace(url.getText(),
                    "<a href='" + url.getExpandedURL() + "'>" + url.getDisplayURL() + "</a>");
        }
        HashtagEntity[] hashtags = status.getHashtagEntities();
        for (HashtagEntity tag: hashtags) {
            content_html = content_html.replace("#" + tag.getText(),
                    "<a href='im.zico.wingtwitter.topic://" + tag.getText() + "'>#" + tag.getText() + "</a>");
        }

        MediaEntity[] mediaEntities = status.getExtendedMediaEntities();
        status.getURLEntities();

        if (mediaEntities != null && mediaEntities.length != 0) {
            mediaUrls = new String[mediaEntities.length];
            for (int i = 0; i < mediaEntities.length; i++) {
                content_html = content_html.replace(mediaEntities[i].getText(), "");
                content = content.replace(mediaEntities[i].getText(), "");
                mediaUrls[i] = mediaEntities[i].getMediaURL();
            }
        }

        htmlContent = convert2Html(content);
        source = Html.fromHtml(status.getSource()).toString();

        retweet_count = status.getRetweetCount();
        favorite_count = status.getFavoriteCount();
        favorited = status.isFavorited();

        in_reply_status_id = status.getInReplyToStatusId();
        in_reply_user_id = status.getInReplyToUserId();
        in_reply_user_screen_name = status.getInReplyToScreenName();
        in_reply_user_name = ""; // no api
    }

    private String convert2Html(String content) {
        Linkify.TransformFilter filter = new Linkify.TransformFilter() {
            public final String transformUrl(final Matcher match, String url) {
                return match.group();
            }
        };

        Pattern mentionPattern = Pattern.compile("@([A-Za-z0-9_-]+)");

//        String mentionScheme = "http://www.twitter.com/";
//        Linkify.addLinks(textView, mentionPattern, mentionScheme, null, filter);

        Pattern hashtagPattern = Pattern.compile("#([A-Za-z0-9_-]+)");
//        String hashtagScheme = "http://www.twitter.com/search/";
//        Linkify.addLinks(textView, hashtagPattern, hashtagScheme, null, filter);

        Pattern urlPattern = Patterns.WEB_URL;
//        Linkify.addLinks(textView, urlPattern, null, null, filter);

        return "";
    }

    public static WingTweet fromCursor(final Cursor cursor) {
        WingTweet wingTweet = new WingTweet();

        wingTweet.account_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.ACCOUNT_ID));
        wingTweet.tweet_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.TWEET_ID));
        wingTweet.user_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.USER_ID));
        wingTweet.user_name = cursor.getString(cursor.getColumnIndex(TweetColumns.USER_NAME));
        wingTweet.screen_name = cursor.getString(cursor.getColumnIndex(TweetColumns.USER_SCREEN_NAME));
        wingTweet.avatar_url = cursor.getString(cursor.getColumnIndex(TweetColumns.USER_AVATAR_URL));

        wingTweet.created_at = cursor.getLong(cursor.getColumnIndex(TweetColumns.CREATED));
        wingTweet.content = cursor.getString(cursor.getColumnIndex(TweetColumns.CONTENT));
        wingTweet.content_html = cursor.getString(cursor.getColumnIndex(TweetColumns.CONTENT_HTML));
        wingTweet.source = cursor.getString(cursor.getColumnIndex(TweetColumns.SOURCE));

        wingTweet.in_reply_status_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.IN_REPLY_TO_STATUS_ID));
        wingTweet.in_reply_user_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.IN_REPLY_TO_USER_ID));
        wingTweet.in_reply_user_name = cursor.getString(cursor.getColumnIndex(TweetColumns.IN_REPLY_TO_USER_NAME));
        wingTweet.in_reply_user_screen_name = cursor.getString(cursor.getColumnIndex(TweetColumns.IN_REPLY_TO_USER_SCREEN_NAME));

        wingTweet.retweet_count = cursor.getInt(cursor.getColumnIndex(TweetColumns.RETWEET_COUNT));
        wingTweet.favorite_count = cursor.getInt(cursor.getColumnIndex(TweetColumns.FAVORITE_COUNT));
        wingTweet.favorited = cursor.getInt(cursor.getColumnIndex(TweetColumns.FAVORITED)) == 1;

        wingTweet.retweet_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.RETWEET_ID));
        wingTweet.retweet_timestamp = cursor.getLong(cursor.getColumnIndex(TweetColumns.RETWEET_TIME));
        wingTweet.retweeted_by_user_id = cursor.getLong(cursor.getColumnIndex(TweetColumns.RETWEETED_BY_USER_ID));
        wingTweet.retweeted_by_user_name = cursor.getString(cursor.getColumnIndex(TweetColumns.RETWEETED_BY_USER_NAME));
        wingTweet.retweeted_by_user_screen_name = cursor.getString(cursor.getColumnIndex(TweetColumns.RETWEETED_BY_USER_SCREEN_NAME));

        wingTweet.mediaUrls = str2Array(cursor.getString(cursor.getColumnIndex(TweetColumns.MEDIAS)));

        return wingTweet;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();

        values.put(TweetColumns.ACCOUNT_ID, account_id);
        values.put(TweetColumns.TWEET_ID, tweet_id);
        values.put(TweetColumns.USER_ID, user_id);
        values.put(TweetColumns.USER_NAME, user_name);
        values.put(TweetColumns.USER_SCREEN_NAME, screen_name);
        values.put(TweetColumns.USER_AVATAR_URL, avatar_url);

        values.put(TweetColumns.CREATED, created_at);
        values.put(TweetColumns.CONTENT, content);
        values.put(TweetColumns.CONTENT_HTML, content_html);
        values.put(TweetColumns.SOURCE, source);

        values.put(TweetColumns.RETWEET_COUNT, retweet_count);
        values.put(TweetColumns.FAVORITE_COUNT, favorite_count);
        values.put(TweetColumns.FAVORITED, favorited);

        values.put(TweetColumns.IN_REPLY_TO_STATUS_ID, in_reply_status_id);
        values.put(TweetColumns.IN_REPLY_TO_USER_ID, in_reply_user_id);
        values.put(TweetColumns.IN_REPLY_TO_STATUS_ID, in_reply_user_name);
        values.put(TweetColumns.IN_REPLY_TO_USER_ID, in_reply_user_screen_name);

        values.put(TweetColumns.RETWEET_ID, retweet_id);
        values.put(TweetColumns.RETWEET_TIME, retweet_timestamp);
        values.put(TweetColumns.RETWEETED_BY_USER_ID, retweeted_by_user_id);
        values.put(TweetColumns.RETWEETED_BY_USER_NAME, retweeted_by_user_name);
        values.put(TweetColumns.RETWEETED_BY_USER_SCREEN_NAME, retweeted_by_user_screen_name);

        values.put(TweetColumns.MEDIAS, array2Str(mediaUrls));

        return values;
    }

    private static String array2Str(String[] array) {
        StringBuilder out = new StringBuilder();
        if (array==null) return "";
        for (int i = 0; i < array.length; i++) {
            out.append(array[i] + (i == array.length ? "" : ","));
        }
        return out.toString();
    }

    private static String[] str2Array(String str) {
        if (str.contains(",")) {
            return str.split(",");
        }
        return null;
    }

//    public static final class CursorIndices {
//
//        private final int _id, account_id, status_id, status_timestamp, user_name, user_screen_name, text_html,
//                text_plain, text_unescaped, user_profile_image_url, is_favorite, is_retweet, is_gap, location,
//                is_protected, is_verified, in_reply_to_status_id, in_reply_to_user_id, in_reply_to_user_name,
//                in_reply_to_user_screen_name, my_retweet_id, retweeted_by_user_name, retweeted_by_user_screen_name,
//                retweet_id, retweeted_by_user_id, user_id, source, retweet_count, favorite_count,
//                is_possibly_sensitive, is_following, medias, first_media, mentions;
//
//        public CursorIndices(final Cursor cursor) {
//            _id = cursor.getColumnIndex(Statuses._ID);
//            account_id = cursor.getColumnIndex(Statuses.ACCOUNT_ID);
//            status_id = cursor.getColumnIndex(Statuses.STATUS_ID);
//            status_timestamp = cursor.getColumnIndex(Statuses.STATUS_TIME);
//            user_name = cursor.getColumnIndex(Statuses.USER_NAME);
//            user_screen_name = cursor.getColumnIndex(Statuses.USER_SCREEN_NAME);
//            text_html = cursor.getColumnIndex(Statuses.BODY_HTML);
//            text_plain = cursor.getColumnIndex(Statuses.BODY_PLAIN);
//            text_unescaped = cursor.getColumnIndex(Statuses.BODY_UNESCAPED);
//            user_profile_image_url = cursor.getColumnIndex(Statuses.USER_PROFILE_AVATAR_URL);
//            is_favorite = cursor.getColumnIndex(Statuses.IS_FAVORITE);
//            is_retweet = cursor.getColumnIndex(Statuses.IS_RETWEET);
//            is_gap = cursor.getColumnIndex(Statuses.IS_GAP);
//            location = cursor.getColumnIndex(Statuses.LOCATION);
//            is_protected = cursor.getColumnIndex(Statuses.IS_PROTECTED);
//            is_verified = cursor.getColumnIndex(Statuses.IS_VERIFIED);
//            in_reply_to_status_id = cursor.getColumnIndex(Statuses.IN_REPLY_TO_STATUS_ID);
//            in_reply_to_user_id = cursor.getColumnIndex(Statuses.IN_REPLY_TO_USER_ID);
//            in_reply_to_user_name = cursor.getColumnIndex(Statuses.IN_REPLY_TO_USER_NAME);
//            in_reply_to_user_screen_name = cursor.getColumnIndex(Statuses.IN_REPLY_TO_USER_SCREEN_NAME);
//            my_retweet_id = cursor.getColumnIndex(Statuses.MY_RETWEET_ID);
//            retweet_id = cursor.getColumnIndex(Statuses.RETWEET_ID);
//            retweeted_by_user_id = cursor.getColumnIndex(Statuses.RETWEETED_BY_USER_ID);
//            retweeted_by_user_name = cursor.getColumnIndex(Statuses.RETWEETED_BY_USER_NAME);
//            retweeted_by_user_screen_name = cursor.getColumnIndex(Statuses.RETWEETED_BY_USER_SCREEN_NAME);
//            user_id = cursor.getColumnIndex(Statuses.USER_ID);
//            source = cursor.getColumnIndex(Statuses.SOURCE);
//            retweet_count = cursor.getColumnIndex(Statuses.RETWEET_COUNT);
//            favorite_count = cursor.getColumnIndex(Statuses.FAVORITE_COUNT);
//            is_possibly_sensitive = cursor.getColumnIndex(Statuses.IS_POSSIBLY_SENSITIVE);
//            is_following = cursor.getColumnIndex(Statuses.IS_FOLLOWING);
//            medias = cursor.getColumnIndex(Statuses.MEDIA);
//            first_media = cursor.getColumnIndex(Statuses.FIRST_MEDIA);
//            mentions = cursor.getColumnIndex(Statuses.MENTIONS);
//        }
//
//        @Override
//        public String toString() {
//            return "CursorIndices{_id=" + _id + ", account_id=" + account_id + ", status_id=" + status_id
//                    + ", status_timestamp=" + status_timestamp + ", user_name=" + user_name + ", user_screen_name="
//                    + user_screen_name + ", text_html=" + text_html + ", text_plain=" + text_plain
//                    + ", text_unescaped=" + text_unescaped + ", user_profile_image_url=" + user_profile_image_url
//                    + ", is_favorite=" + is_favorite + ", is_retweet=" + is_retweet + ", is_gap=" + is_gap
//                    + ", location=" + location + ", is_protected=" + is_protected + ", is_verified=" + is_verified
//                    + ", in_reply_to_status_id=" + in_reply_to_status_id + ", in_reply_to_user_id="
//                    + in_reply_to_user_id + ", in_reply_to_user_name=" + in_reply_to_user_name
//                    + ", in_reply_to_user_screen_name=" + in_reply_to_user_screen_name + ", my_retweet_id="
//                    + my_retweet_id + ", retweeted_by_user_name=" + retweeted_by_user_name
//                    + ", retweeted_by_user_screen_name=" + retweeted_by_user_screen_name + ", retweet_id=" + retweet_id
//                    + ", retweeted_by_user_id=" + retweeted_by_user_id + ", user_id=" + user_id + ", source=" + source
//                    + ", retweet_count=" + retweet_count + ", favorite_count=" + favorite_count
//                    + ", is_possibly_sensitive=" + is_possibly_sensitive + ", is_following=" + is_following
//                    + ", medias=" + medias + ", first_media=" + first_media + ", mentions=" + mentions + "}";
//        }
//    }

}
