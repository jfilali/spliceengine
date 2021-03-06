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
/**
 * <p>
 * Ping the server, waiting till it comes up.
 * </p>
 *
 */
package com.splicemachine.dbTesting.functionTests.tests.junitTests.compatibility;

import com.splicemachine.db.drda.NetworkServerControl;

public	class	Pinger
{
	/////////////////////////////////////////////////////////////
	//
	//	CONSTANTS
	//
	/////////////////////////////////////////////////////////////

	public	static	final			long	SLEEP_TIME_MILLIS = 5000L;

	public	static	final			int		SUCCESS_EXIT = 0;
	public	static	final			int		FAILURE_EXIT = 1;
	
	/////////////////////////////////////////////////////////////
	//
	//	STATE
	//
	/////////////////////////////////////////////////////////////
	
	/////////////////////////////////////////////////////////////
	//
	//	CONSTRUCTOR
	//
	/////////////////////////////////////////////////////////////
	
	public	Pinger() {}

	/////////////////////////////////////////////////////////////
	//
	//	ENTRY POINT
	//
	/////////////////////////////////////////////////////////////
	
	public	static	void	main( String[] args )
		throws Exception
	{
		Pinger	me = new Pinger();
		
		me.ping( 5 );
	}	

	/////////////////////////////////////////////////////////////
	//
	//	MINIONS
	//
	/////////////////////////////////////////////////////////////
	
	private	void	println( String text )
	{
		System.err.println( text );
		System.err.flush();
	}

	private	void	exit( int exitStatus )
	{
		Runtime.getRuntime().exit( exitStatus );
	}

	/////////////////////
	//
	//	SERVER MANAGEMENT
	//
	/////////////////////

	/**
	 * <p>
	 * Checks to see that the server is up. If the server doesn't
	 * come up in a reasonable amount of time, brings down the VM.
	 * </p>
	 */
	public	void	ping( int iterations )
		throws Exception
	{
		ping( new NetworkServerControl(), iterations );
	}


	private	void	ping( NetworkServerControl controller, int iterations )
		throws Exception
	{
		Exception	finalException = null;
		
		for ( int i = 0; i < iterations; i++ )
		{
			try {
				controller.ping();

				return;
			}
			catch (Exception e) { finalException = e; }
			
			Thread.sleep( SLEEP_TIME_MILLIS );
		}

		println( "Server did not come up: " + finalException.getMessage() );
		exit( FAILURE_EXIT );
	}


}

