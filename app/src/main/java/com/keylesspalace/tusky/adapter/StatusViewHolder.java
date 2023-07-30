/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Filter;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.entity.TimelineAccount;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.NumberUtils;
import com.keylesspalace.tusky.util.SmartLengthInputFilter;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.util.StringUtils;
import com.keylesspalace.tusky.util.TextWithContentDescription;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import at.connyduck.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    @SuppressLint("unused")
    private static final String TAG = "StatusBaseViewHolder";
    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private final TextView statusInfo;
    private final Button contentCollapseButton;
    private final TextView favouritedCountLabel;
    private final TextView reblogsCountLabel;

    /** String builder to use when creating statusInfo view content */
    private final SpannableStringBuilder statusInfoSpannableStringBuilder = new SpannableStringBuilder();

    /** Span that contains the "reply" icon */
    private final SpannableString iconReplySpan;
    /** Span that contains the "reblog" icon */
    private final SpannableString iconReblogSpan;

    public StatusViewHolder(View itemView) {
        super(itemView);
        statusInfo = itemView.findViewById(R.id.status_info);
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content);
        favouritedCountLabel = itemView.findViewById(R.id.status_favourites_count);
        reblogsCountLabel = itemView.findViewById(R.id.status_insets);

        Context context = statusInfo.getContext();
        ImageSpan span = new ImageSpan(
            AppCompatResources.getDrawable(context, R.drawable.ic_reply_18dp),
            DynamicDrawableSpan.ALIGN_BASELINE
        );

        Drawable drawable = AppCompatResources.getDrawable(context, R.drawable.ic_reply_18dp);
        assert drawable != null;
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

        iconReplySpan = new SpannableString("  ");
        iconReplySpan.setSpan(
            new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_BOTTOM),
            0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        drawable = AppCompatResources.getDrawable(context, R.drawable.ic_reblog_18dp);
        assert drawable != null;
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

        iconReblogSpan = new SpannableString("  ");
        iconReblogSpan.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_BOTTOM),
            0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    @Override
    public void setupWithStatus(@NonNull StatusViewData.Concrete status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @Nullable Object payloads) {
        if (payloads == null) {

            boolean sensitive = !TextUtils.isEmpty(status.getActionable().getSpoilerText());
            boolean expanded = status.isExpanded();

            setupCollapsedState(sensitive, expanded, status, listener);

            if (status.getFilterAction() == Filter.Action.WARN) {
                hideStatusInfo();
            } else {
                setStatusInfo(statusInfo.getContext(), status, statusDisplayOptions);
                statusInfo.setOnClickListener(v -> listener.onOpenReblog(getBindingAdapterPosition()));
            }

        }

        reblogsCountLabel.setVisibility(statusDisplayOptions.showStatsInline() ? View.VISIBLE : View.INVISIBLE);
        favouritedCountLabel.setVisibility(statusDisplayOptions.showStatsInline() ? View.VISIBLE : View.INVISIBLE);
        setFavouritedCount(status.getActionable().getFavouritesCount());
        setReblogsCount(status.getActionable().getReblogsCount());

        super.setupWithStatus(status, listener, statusDisplayOptions, payloads);
    }

    /**
     * Sets the content and visibility of the status info view.
     * <p>
     * The view contains 0 or more of the following pieces of information:
     * <p>
     * - A reblog marker, with the name of the account that performed the reblog
     * - A thread marker
     *   - "In reply", if it's a reply, possibly with the name of the account it's replying to
     * - (not implemented) A hashtag marker, showing any hashtags the user follows for this timeline
     *   that are in the post.
     */
    private void setStatusInfo(
        Context context,
        StatusViewData.Concrete statusViewData,
        StatusDisplayOptions statusDisplayOptions
    ) {
        // Reblogging
        TextWithContentDescription reblogText = getRebloggedByText(
            context,
            statusViewData,
            statusDisplayOptions
        );

        // Reply
        TextWithContentDescription replyText = getReplyText(
            context,
            statusViewData,
            statusDisplayOptions
        );

        // TODO: Hashtags

        if (reblogText == null && replyText == null) {
            hideStatusInfo();
            return;
        }

        statusInfoSpannableStringBuilder.clear();
        statusInfoSpannableStringBuilder.clearSpans();

        if (replyText != null && reblogText != null) {
            statusInfoSpannableStringBuilder
                .append(iconReplySpan)
                .append(replyText.getText())
                .append(" • ")
                .append(iconReblogSpan)
                .append(reblogText.getText());
            statusInfo.setText(statusInfoSpannableStringBuilder);
            statusInfo.setContentDescription(
                context.getString(
                    R.string.post_reply_and_boost_description,
                    replyText.getContentDescription(),
                    reblogText.getContentDescription()
                )
            );
        } else if (reblogText == null) {
            statusInfoSpannableStringBuilder
                .append(iconReplySpan)
                .append(replyText.getText());
            statusInfo.setText(statusInfoSpannableStringBuilder);
            statusInfo.setContentDescription(replyText.getContentDescription());
        } else {
            statusInfoSpannableStringBuilder
                .append(iconReblogSpan)
                .append(reblogText.getText());
            statusInfo.setText(statusInfoSpannableStringBuilder);
            statusInfo.setContentDescription(reblogText.getContentDescription());
        }

        statusInfo.setVisibility(View.VISIBLE);
    }


    /**
     * @return text to display in the "reply" portion of the statusInfo.
     */
    @Nullable
    private TextWithContentDescription getReplyText(
        Context context,
        @NonNull StatusViewData.Concrete statusViewData,
        @NonNull StatusDisplayOptions statusDisplayOptions
    ) {
        Status status = statusViewData.getActionable();

        // This is not a reply
        String replyAccountId = status.getInReplyToAccountId();
        if (replyAccountId == null) return null;

        // It's a reply, get the account it's replying to (may be null if the account info
        // wasn't known)
        TimelineAccount inReplyToAccount = statusViewData.getInReplyToAccount();

        // May not know the account details of the account it's replying to. Try and find the
        // username in the mentions. Fall back to the default string if it's not there.
        if (inReplyToAccount == null) {
            for (Status.Mention mention : status.getMentions()) {
                if (mention.getId().equals(replyAccountId)) {
                    String username = "@" + mention.getUsername();
                    return new TextWithContentDescription(
                        context.getString(R.string.post_reply_format, username),
                        context.getString(R.string.post_reply_format_description, username)
                    );
                }
            }
            return new TextWithContentDescription(
                context.getString(R.string.post_reply),
                context.getString(R.string.post_reply)
            );
        }

        // Replying to themselves?
        if (inReplyToAccount.getId().equals(status.getAccount().getId())) {
            return new TextWithContentDescription(
                context.getString(R.string.post_reply_self),
                context.getString(R.string.post_reply_self_description)
            );
        }

        String name = inReplyToAccount.getName();
        CharSequence wrappedName = StringUtils.unicodeWrap(name);
        CharSequence text = context.getString(R.string.post_reply_format, wrappedName);

        return new TextWithContentDescription(
            CustomEmojiHelper.emojify(
                text, inReplyToAccount.getEmojis(), statusInfo,
                statusDisplayOptions.animateEmojis()
            ),
            text
        );
    }

    /**
     * @return text to display in the "boosted" portion of the statusInfo.
     */
    @Nullable
    private TextWithContentDescription getRebloggedByText(
        Context context,
        @NonNull StatusViewData.Concrete statusViewData,
        @NonNull final StatusDisplayOptions statusDisplayOptions
    ) {
        Status rebloggedStatus = statusViewData.getRebloggingStatus();

        if (rebloggedStatus == null) return null;

        if (statusViewData.getStatus().getAccount().getId().equals(rebloggedStatus.getId())) {
            return new TextWithContentDescription(
                context.getString(R.string.post_boost_self),
                context.getString(R.string.post_boost_self_description)
            );
        }

        String name = rebloggedStatus.getAccount().getName();
        CharSequence wrappedName = StringUtils.unicodeWrap(name);
        CharSequence text = context.getString(R.string.post_boosted_format, wrappedName);
        return new TextWithContentDescription(
            CustomEmojiHelper.emojify(
                text, rebloggedStatus.getAccount().getEmojis(), statusInfo, statusDisplayOptions.animateEmojis()
            ),
            context.getString(R.string.post_boosted_format_description, wrappedName)
        );
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    protected void setPollInfo(final boolean ownPoll) {
        statusInfo.setText(ownPoll ? R.string.poll_ended_created : R.string.poll_ended_voted);
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0);
        statusInfo.setCompoundDrawablePadding(Utils.dpToPx(statusInfo.getContext(), 10));
        statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.getContext(), 28), 0, 0, 0);
        statusInfo.setVisibility(View.VISIBLE);
    }

    protected void setReblogsCount(int reblogsCount) {
        reblogsCountLabel.setText(NumberUtils.formatNumber(reblogsCount, 1000));
    }

    protected void setFavouritedCount(int favouritedCount) {
        favouritedCountLabel.setText(NumberUtils.formatNumber(favouritedCount, 1000));
    }

    public void hideStatusInfo() {
        statusInfo.setVisibility(View.GONE);
    }

    private void setupCollapsedState(boolean sensitive,
                                     boolean expanded,
                                     final StatusViewData.Concrete status,
                                     final StatusActionListener listener) {
        /* input filter for TextViews have to be set before text */
        if (status.isCollapsible() && (!sensitive || expanded)) {
            contentCollapseButton.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    listener.onContentCollapsedChange(!status.isCollapsed(), position);
            });

            contentCollapseButton.setVisibility(View.VISIBLE);
            if (status.isCollapsed()) {
                contentCollapseButton.setText(R.string.post_content_warning_show_more);
                content.setFilters(COLLAPSE_INPUT_FILTER);
            } else {
                contentCollapseButton.setText(R.string.post_content_warning_show_less);
                content.setFilters(NO_INPUT_FILTER);
            }
        } else {
            contentCollapseButton.setVisibility(View.GONE);
            content.setFilters(NO_INPUT_FILTER);
        }
    }

    public void showStatusContent(boolean show) {
        super.showStatusContent(show);
        contentCollapseButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void toggleExpandedState(boolean sensitive,
                                       boolean expanded,
                                       @NonNull StatusViewData.Concrete status,
                                       @NonNull StatusDisplayOptions statusDisplayOptions,
                                       @NonNull final StatusActionListener listener) {

        setupCollapsedState(sensitive, expanded, status, listener);

        super.toggleExpandedState(sensitive, expanded, status, statusDisplayOptions, listener);
    }
}
