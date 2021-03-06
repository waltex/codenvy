/*
 * Copyright (c) [2012] - [2017] Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.codenvy.resource.api.license;

import com.codenvy.resource.model.ProvidedResources;
import java.util.List;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;

/**
 * Bridge class that link license and resources granting mechanisms.
 *
 * @author Sergii Leschenko
 */
public interface ResourcesProvider {
  /**
   * Returns list of provided resources for given account.
   *
   * @param accountId account id
   * @return list of provided resources for given account or empty list if there are not any
   *     resources for given account
   * @throws NotFoundException when account with specified id was not found
   * @throws ServerException when some exception occurs
   */
  List<ProvidedResources> getResources(String accountId) throws ServerException, NotFoundException;
}
