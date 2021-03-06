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

package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.SQLState;

/**
 * A NOPStatement node is for statements that don't do anything.  At the
 * time of this writing, the only statements that use it are
 * SET DB2J_DEBUG ON and SET DB2J_DEBUG OFF.  Both of these are
 * executed in the parser, so the statements don't do anything at execution
 */

public class NOPStatementNode extends StatementNode
{
	public String statementToString()
	{
		return "NO-OP";
	}

	/**
	 * Bind this NOP statement.  This throws an exception, because NOP
	 * statements by definition stop after parsing.
	 *
	 *
	 * @exception StandardException		Always thrown to stop after parsing
	 */
	public void bindStatement() throws StandardException
	{
		/*
		** Prevent this statement from getting to execution by throwing
		** an exception during the bind phase.  This way, we don't
		** have to generate a class.
		*/

		throw StandardException.newException(SQLState.LANG_PARSE_ONLY);
	}

	int activationKind()
	{
		   return StatementNode.NEED_NOTHING_ACTIVATION;
	}
}
