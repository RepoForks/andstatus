/**
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.ContextMenuItem;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.user.UserListType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

public enum MessageListContextMenuItem implements ContextMenuItem {
    REPLY(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return MessageEditorData.newEmpty(menu.getMyActor()).
                    setInReplyToId(menu.getMsgId()).addMentionsToText();
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.menuContainer.getMessageEditor().startEditingMessage(editorData);
        }
    },
    EDIT(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return MessageEditorData.load(menu.getMsgId());
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.menuContainer.getMessageEditor().startEditingMessage(editorData);
        }
    },
    RESEND(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(
                    MyQuery.msgIdToLongColumnValue(MsgTable.SENDER_ID, menu.getMsgId()));
            CommandData commandData = CommandData.newUpdateStatus(ma, menu.getMsgId());
            MyServiceManager.sendManualForegroundCommand(commandData);
            return null;
        }
    },
    REPLY_TO_CONVERSATION_PARTICIPANTS(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return MessageEditorData.newEmpty(menu.getMyActor()).
                    setInReplyToId(menu.getMsgId()).setReplyToConversationParticipants(true).
                    addMentionsToText();
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.menuContainer.getMessageEditor().startEditingMessage(editorData);
        }
    },
    REPLY_TO_MENTIONED_USERS(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return MessageEditorData.newEmpty(menu.getMyActor()).
                    setInReplyToId(menu.getMsgId()).setReplyToMentionedUsers(true).
                    addMentionsToText();
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.menuContainer.getMessageEditor().startEditingMessage(editorData);
        }
    },
    DIRECT_MESSAGE(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return MessageEditorData.newEmpty(menu.getMyActor())
                    .setRecipientId(MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, menu.getMsgId()));
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                menu.menuContainer.getMessageEditor().startEditingMessage(editorData);
            }
        }
    },
    FAVORITE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.CREATE_FAVORITE, editorData);
        }
    },
    DESTROY_FAVORITE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.DESTROY_FAVORITE, editorData);
        }
    },
    REBLOG() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.REBLOG, editorData);
        }
    },
    DESTROY_REBLOG() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.DESTROY_REBLOG, editorData);
        }
    },
    DESTROY_STATUS() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.DESTROY_STATUS, editorData);
        }
    },
    SHARE(false) {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            MessageShare messageShare = new MessageShare(menu.getOrigin(), menu.getMsgId(),
                    menu.imageFilename);
            messageShare.share(menu.getActivity());
        }
    },
    COPY_TEXT(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            String body = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, menu.getMsgId());
            if (menu.getOrigin().isHtmlContentAllowed()) {
                body = MyHtml.fromHtml(body);
            }
            return MessageEditorData.newEmpty(menu.getMyActor()).setBody(body);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
        }
    },
    COPY_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return MessageEditorData.newEmpty(menu.getMyActor()).
                    appendMentionedUserToText(
                    MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, menu.getMsgId()));
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
        }
    },
    SENDER_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return fillUserId(menu.getMyActor(), menu.getMsgId(), MsgTable.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                menu.switchTimelineActivityView(
                        Timeline.getTimeline(menu.getActivity().getMyContext(), 0, TimelineType.USER,
                        null, editorData.recipientId, menu.getOrigin(), ""));
            }
        }
    },
    AUTHOR_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return fillUserId(menu.getMyActor(), menu.getMsgId(), MsgTable.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                menu.switchTimelineActivityView(
                        Timeline.getTimeline(menu.getActivity().getMyContext(), 0, TimelineType.USER,
                        null, editorData.recipientId, menu.getOrigin(), ""));
            }
        }
    },
    FOLLOW_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return fillUserId(menu.getMyActor(), menu.getMsgId(), MsgTable.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.FOLLOW_USER, editorData);
        }
    },
    STOP_FOLLOWING_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return fillUserId(menu.getMyActor(), menu.getMsgId(), MsgTable.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.STOP_FOLLOWING_USER, editorData);
        }
    },
    FOLLOW_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return fillUserId(menu.getMyActor(), menu.getMsgId(), MsgTable.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.FOLLOW_USER, editorData);
        }
    },
    STOP_FOLLOWING_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MessageContextMenu menu) {
            return fillUserId(menu.getMyActor(), menu.getMsgId(), MsgTable.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.STOP_FOLLOWING_USER, editorData);
        }
    },
    PROFILE(),
    BLOCK(),
    ACT_AS_FIRST_OTHER_USER() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.setMyActor(editorData.ma.firstOtherAccountOfThisOrigin());
            menu.showContextMenu();
        }
    },
    ACT_AS() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            AccountSelector.selectAccount(menu.getActivity(),
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, editorData.ma.getOriginId());
        }
    },
    OPEN_MESSAGE_PERMALINK(false) {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            MessageShare messageShare = new MessageShare(menu.getOrigin(), menu.getMsgId(),
                    menu.imageFilename);
            messageShare.openPermalink(menu.getActivity());
        }
    },
    VIEW_IMAGE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            MessageShare messageShare = new MessageShare(menu.getOrigin(), menu.getMsgId(),
                    menu.imageFilename);
            messageShare.viewImage(menu.getActivity());
        }
    },
    OPEN_CONVERSATION() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            Uri uri = MatchedUri.getTimelineItemUri(
                    Timeline.getTimeline(TimelineType.EVERYTHING, null, 0, menu.getOrigin()),
                    menu.getMsgId());
            String action = menu.getActivity().getIntent().getAction();
            if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, setData=" + uri);
                }
                menu.getActivity().setResult(Activity.RESULT_OK, new Intent().setData(uri));
            } else {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, startActivity=" + uri);
                }
                menu.getActivity().startActivity(MyAction.VIEW_CONVERSATION.getIntent(uri));
            }
        }
    },
    USERS_OF_MESSAGE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            Uri uri = MatchedUri.getUserListUri(editorData.ma.getUserId(),
                    UserListType.USERS_OF_MESSAGE, menu.getOrigin().getId(),
                    menu.getMsgId());
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, startActivity=" + uri);
            }
            menu.getActivity().startActivity(MyAction.VIEW_USERS.getIntent(uri));
        }
    },
    SHOW_DUPLICATES {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.getActivity().updateList(TriState.FALSE, menu.getMsgId(), false);
        }
    },
    COLLAPSE_DUPLICATES {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.getActivity().updateList(TriState.TRUE, menu.getMsgId(), false);
        }
    },
    GET_MESSAGE {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newItemCommand(CommandEnum.GET_STATUS, menu.getMyActor(),
                    menu.getMsgId())
            );
        }
    },
    MESSAGE_LINK {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            MessageShare.openLink(menu.getActivity(), extractUrlFromTitle(menu.getSelectedItemTitle()));
        }

        private String extractUrlFromTitle(@NonNull String title) {
            int ind = title.indexOf(MESSAGE_LINK_SEPARATOR);
            if (ind < 0) {
                return title;
            }
            return title.substring(ind + MESSAGE_LINK_SEPARATOR.length());
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    public static final String MESSAGE_LINK_SEPARATOR = ": ";
    private static final String TAG = MessageListContextMenuItem.class.getSimpleName();
    private final boolean mIsAsync;

    MessageListContextMenuItem() {
        this(false);
    }

    MessageListContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
    }

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }
    
    public static MessageListContextMenuItem fromId(int id) {
        for (MessageListContextMenuItem item : MessageListContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    protected void copyMessageText(MessageEditorData editorData) {
        MyLog.v(this, "text='" + editorData.body + "'");
        if (!TextUtils.isEmpty(editorData.body)) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) MyContextHolder.get().context().
                    getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(I18n.trimTextAt(editorData.body, 40), editorData.body);
            clipboard.setPrimaryClip(clip);
            MyLog.v(this, "clip='" + clip.toString() + "'");
        }
    }

    public void addTo(Menu menu, int order, int titleRes) {
        menu.add(Menu.NONE, this.getId(), order, titleRes);
    }

    public void addTo(Menu menu, int order, CharSequence title) {
        menu.add(Menu.NONE, this.getId(), order, title);
    }
    
    public boolean execute(MessageContextMenu menu) {
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(menu);
        } else {
            executeOnUiThread(menu, MessageEditorData.newEmpty(menu.getMyActor()).
                    setMsgId(menu.getMsgId()));
        }
        return false;
    }
    
    private void executeAsync1(final MessageContextMenu menu) {
        AsyncTaskLauncher.execute(TAG, true,
                new MyAsyncTask<Void, Void, MessageEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected MessageEditorData doInBackground2(Void... params) {
                        MyLog.v(MessageListContextMenuItem.this,
                                "execute async started. msgId=" + menu.getMsgId());
                        return executeAsync(menu);
                    }

                    @Override
                    protected void onPostExecute(MessageEditorData editorData) {
                        MyLog.v(MessageListContextMenuItem.this, "execute async ended");
                        executeOnUiThread(menu, editorData);
                    }
                }
        );
    }

    MessageEditorData executeAsync(MessageContextMenu menu) {
        return MessageEditorData.newEmpty(menu.getMyActor());
    }

    MessageEditorData fillUserId(MyAccount ma, long msgId, String msgUserIdColumnName) {
        return MessageEditorData.newEmpty(ma)
                .setRecipientId(MyQuery.msgIdToUserId(msgUserIdColumnName, msgId));
    }

    void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
        // Empty
    }
    
    void sendUserCommand(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newUserCommand(command, null, editorData.ma.getOrigin(), editorData.recipientId, ""));
    }
    
    void sendMsgCommand(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newItemCommand(command, editorData.ma, editorData.getMsgId()));
    }
}
