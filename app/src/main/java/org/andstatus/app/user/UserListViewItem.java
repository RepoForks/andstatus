/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.user;

import android.graphics.drawable.Drawable;

import org.andstatus.app.ViewItem;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;

import java.util.HashSet;
import java.util.Set;

public class UserListViewItem implements ViewItem {
    boolean populated = false;
    final MbUser mbUser;
    Drawable avatarDrawable = null;
    Set<Long> myFollowers = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserListViewItem that = (UserListViewItem) o;
        return mbUser.equals(that.mbUser);
    }

    @Override
    public int hashCode() {
        return mbUser.hashCode();
    }

    private UserListViewItem(MbUser mbUser) {
        this.mbUser = mbUser;
    }

    public static UserListViewItem getEmpty(String description) {
        MbUser mbUser = MbUser.getEmpty();
        mbUser.setDescription(description);
        return fromMbUser(mbUser);
    }

    public static UserListViewItem fromUserId(Origin origin, long userId) {
        MbUser mbUser = MbUser.getEmpty();
        if (userId != 0) {
            mbUser = MbUser.fromOriginAndUserOid(origin.getId(),
                    MyQuery.idToOid(OidEnum.USER_OID, userId, 0));
            mbUser.userId = userId;
            mbUser.setWebFingerId(MyQuery.userIdToWebfingerId(userId));
        }
        return fromMbUser(mbUser);
    }

    public static UserListViewItem fromMbUser(MbUser mbUser) {
        return new UserListViewItem(mbUser);
    }

    public long getUserId() {
        return mbUser.userId;
    }

    public Drawable getAvatar() {
        return avatarDrawable;
    }

    @Override
    public String toString() {
        return "UserListViewItem{" +
                mbUser +
                '}';
    }

    public boolean isEmpty() {
        return mbUser.isEmpty();
    }

    public boolean userIsFollowedBy(MyAccount ma) {
        return myFollowers.contains(ma.getUserId());
    }

    @Override
    public long getId() {
        return getUserId();
    }
}
