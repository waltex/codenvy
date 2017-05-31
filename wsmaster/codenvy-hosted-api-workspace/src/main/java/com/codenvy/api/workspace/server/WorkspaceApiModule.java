/*
 *  [2012] - [2017] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.api.workspace.server;

import com.codenvy.api.machine.server.filters.RecipePermissionsFilter;
import com.codenvy.api.machine.server.recipe.RecipeCreatorPermissionsProvider;
import com.codenvy.api.machine.server.recipe.RecipeDomain;
import com.codenvy.api.permission.server.SuperPrivilegesChecker;
import com.codenvy.api.permission.server.filter.check.RemovePermissionsChecker;
import com.codenvy.api.permission.server.filter.check.SetPermissionsChecker;
import com.codenvy.api.permission.shared.model.PermissionsDomain;
import com.codenvy.api.workspace.server.filters.PublicPermissionsRemoveChecker;
import com.codenvy.api.workspace.server.filters.RecipeDomainSetPermissionsChecker;
import com.codenvy.api.workspace.server.filters.RecipeScriptDownloadPermissionFilter;
import com.codenvy.api.workspace.server.filters.StackDomainSetPermissionsChecker;
import com.codenvy.api.workspace.server.filters.StackPermissionsFilter;
import com.codenvy.api.workspace.server.filters.WorkspacePermissionsFilter;
import com.codenvy.api.workspace.server.stack.StackCreatorPermissionsProvider;
import com.codenvy.api.workspace.server.stack.StackDomain;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

/**
 * @author Sergii Leschenko
 */
public class WorkspaceApiModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WorkspacePermissionsFilter.class);
        bind(RecipePermissionsFilter.class);
        bind(StackPermissionsFilter.class);
        bind(RecipeScriptDownloadPermissionFilter.class);

        bind(WorkspaceCreatorPermissionsProvider.class).asEagerSingleton();
        bind(StackCreatorPermissionsProvider.class).asEagerSingleton();
        bind(RecipeCreatorPermissionsProvider.class).asEagerSingleton();

        Multibinder.newSetBinder(binder(), PermissionsDomain.class, Names.named(SuperPrivilegesChecker.SUPER_PRIVILEGED_DOMAINS))
                   .addBinding().to(WorkspaceDomain.class);

        MapBinder.newMapBinder(binder(), String.class, SetPermissionsChecker.class)
                 .addBinding(StackDomain.DOMAIN_ID)
                 .to(StackDomainSetPermissionsChecker.class);
        MapBinder.newMapBinder(binder(), String.class, SetPermissionsChecker.class)
                 .addBinding(RecipeDomain.DOMAIN_ID)
                 .to(RecipeDomainSetPermissionsChecker.class);

        MapBinder.newMapBinder(binder(), String.class, RemovePermissionsChecker.class)
                 .addBinding(StackDomain.DOMAIN_ID)
                 .to(PublicPermissionsRemoveChecker.class);
        MapBinder.newMapBinder(binder(), String.class, RemovePermissionsChecker.class)
                 .addBinding(RecipeDomain.DOMAIN_ID)
                 .to(PublicPermissionsRemoveChecker.class);
    }

}
