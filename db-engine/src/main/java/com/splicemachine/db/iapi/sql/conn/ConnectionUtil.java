/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.iapi.sql.conn;

import com.splicemachine.db.iapi.services.context.ContextService;
import com.splicemachine.db.iapi.services.i18n.MessageService;
import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.error.ExceptionSeverity;

import java.sql.SQLException;

public class ConnectionUtil {

	/**
		Get the current LanguageConnectionContext.
		Used by public api code that needs to ensure it
		is in the context of a SQL connection.

		@exception SQLException Caller is not in the context of a connection.
	*/
	public static LanguageConnectionContext getCurrentLCC()
		throws SQLException {

			LanguageConnectionContext lcc = (LanguageConnectionContext)
				ContextService.getContextOrNull(LanguageConnectionContext.CONTEXT_ID);

			if (lcc == null)
				throw new SQLException(
							// No current connection
							MessageService.getTextMessage(
											SQLState.NO_CURRENT_CONNECTION),
							SQLState.NO_CURRENT_CONNECTION,
							ExceptionSeverity.SESSION_SEVERITY);

			return lcc;
	}
}
