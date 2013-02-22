package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.derby.iapi.sql.execute.SpliceNoPutResultSet;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManager;
import com.splicemachine.derby.impl.store.access.base.SpliceConglomerate;
import com.splicemachine.derby.impl.store.access.btree.IndexConglomerate;
import com.splicemachine.derby.utils.Scans;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.services.loader.GeneratedMethod;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.execute.CursorResultSet;
import org.apache.derby.iapi.sql.execute.ExecIndexRow;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.NoPutResultSet;
import org.apache.derby.iapi.store.access.Qualifier;
import org.apache.derby.iapi.store.access.ScanController;
import org.apache.derby.iapi.types.RowLocation;
import org.apache.derby.impl.sql.GenericStorablePreparedStatement;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public abstract class ScanOperation extends SpliceBaseOperation implements CursorResultSet{
	private static Logger LOG = Logger.getLogger(ScanOperation.class);
	private static long serialVersionUID=5l;
	protected int lockMode;
	protected int isolationLevel;
	protected ExecRow candidate;
	protected FormatableBitSet accessedCols;
	protected String resultRowAllocatorMethodName;
	protected String scanQualifiersField;
	protected int startSearchOperator;
	protected int stopSearchOperator;
	protected String startKeyGetterMethodName;
	protected String stopKeyGetterMethodName;
	protected boolean sameStartStopPosition;
	protected Qualifier[][] scanQualifiers;
	protected ExecIndexRow stopPosition;
	protected ExecIndexRow startPosition;
	protected SpliceConglomerate conglomerate;
	protected long conglomId;
	protected boolean isKeyed;
	private GeneratedMethod startKeyGetter;
	private GeneratedMethod stopKeyGetter;

	private int colRefItem;
	protected GeneratedMethod resultRowAllocator;


	public ScanOperation () {
		super();
	}

	public ScanOperation (long conglomId, Activation activation, int resultSetNumber,
												GeneratedMethod startKeyGetter, int startSearchOperator,
												GeneratedMethod stopKeyGetter, int stopSearchOperator,
												boolean sameStartStopPosition,
												String scanQualifiersField,
												GeneratedMethod resultRowAllocator,
												int lockMode, boolean tableLocked, int isolationLevel,
												int colRefItem,
												double optimizerEstimatedRowCount,
												double optimizerEstimatedCost) throws StandardException {
		super(activation, resultSetNumber, optimizerEstimatedRowCount, optimizerEstimatedCost);
		this.lockMode = lockMode;
		this.isolationLevel = isolationLevel;
		this.resultRowAllocatorMethodName = resultRowAllocator.getMethodName();
		this.colRefItem = colRefItem;
		this.scanQualifiersField = scanQualifiersField;
		this.startKeyGetterMethodName = (startKeyGetter!= null) ? startKeyGetter.getMethodName() : null;
		this.stopKeyGetterMethodName = (stopKeyGetter!= null) ? stopKeyGetter.getMethodName() : null;
		this.startSearchOperator = startSearchOperator;
		this.stopSearchOperator = stopSearchOperator;
		this.sameStartStopPosition = sameStartStopPosition;
		this.startKeyGetterMethodName = (startKeyGetter!= null) ? startKeyGetter.getMethodName() : null;
		this.stopKeyGetterMethodName = (stopKeyGetter!= null) ? stopKeyGetter.getMethodName() : null;
		this.sameStartStopPosition = sameStartStopPosition;
		this.conglomId = conglomId;
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		SpliceLogUtils.trace(LOG, "readExternal");
		super.readExternal(in);
		lockMode = in.readInt();
		isolationLevel = in.readInt();
		resultRowAllocatorMethodName = in.readUTF();
		scanQualifiersField = readNullableString(in);
		startKeyGetterMethodName = readNullableString(in);
		stopKeyGetterMethodName = readNullableString(in);
		stopSearchOperator = in.readInt();
		startSearchOperator = in.readInt();
		sameStartStopPosition = in.readBoolean();
		conglomId = in.readLong();
		colRefItem = in.readInt();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SpliceLogUtils.trace(LOG, "writeExternal");
		super.writeExternal(out);
		out.writeInt(lockMode);
		out.writeInt(isolationLevel);
		out.writeUTF(resultRowAllocatorMethodName);
		writeNullableString(scanQualifiersField, out);
		writeNullableString(startKeyGetterMethodName, out);
		writeNullableString(stopKeyGetterMethodName, out);
		out.writeInt(stopSearchOperator);
		out.writeInt(startSearchOperator);
		out.writeBoolean(sameStartStopPosition);
		out.writeLong(conglomId);
		out.writeInt(colRefItem);
	}
	
	@Override
    public void init(SpliceOperationContext context){
        SpliceLogUtils.trace(LOG, "init called");
        super.init(context);
        GenericStorablePreparedStatement statement = context.getPreparedStatement();
        this.accessedCols = colRefItem != -1 ? (FormatableBitSet)(statement.getSavedObject(colRefItem)) : null;
        SpliceLogUtils.trace(LOG,"<%d> colRefItem=%d,accessedCols=%s",conglomId,colRefItem,accessedCols);
        try {
            resultRowAllocator = statement.getActivationClass()
                    .getMethod(resultRowAllocatorMethodName);
            this.conglomerate = (SpliceConglomerate)
                    ((SpliceTransactionManager) activation.getTransactionController())
                            .findConglomerate(conglomId);
            this.isKeyed = conglomerate.getTypeFormatId() == IndexConglomerate.FORMAT_NUMBER;
            if (startKeyGetterMethodName != null) {
                startKeyGetter = statement.getActivationClass().getMethod(startKeyGetterMethodName);
            }
            if (stopKeyGetterMethodName != null) {
                stopKeyGetter = statement.getActivationClass().getMethod(stopKeyGetterMethodName);
            }
            candidate = (ExecRow) resultRowAllocator.invoke(activation);
            currentRow = getCompactRow(context.getLanguageConnectionContext(), candidate,
                    accessedCols, isKeyed);
        } catch (Exception e) {
            SpliceLogUtils.logAndThrowRuntime(LOG, "Operation Init Failed!", e);
        }
    }

    @Override
    public NoPutResultSet executeScan() {
        SpliceLogUtils.trace(LOG, "executeScan");
        return new SpliceNoPutResultSet(activation,this, getMapRowProvider(this,getExecRowDefinition()));
    }
	@Override
	public SpliceOperation getLeftOperation() {
		SpliceLogUtils.trace(LOG, "getLeftOperation");
		return null;
	}
	@Override
	public RowLocation getRowLocation() throws StandardException {
		SpliceLogUtils.trace(LOG, "getRowLocation %s",currentRowLocation);
		return currentRowLocation;
	}

	@Override
	public ExecRow getCurrentRow() throws StandardException {
		SpliceLogUtils.trace(LOG, "getCurrentRow %s",currentRow);
		return currentRow;
	}
	
	protected void initIsolationLevel() {
		SpliceLogUtils.trace(LOG, "initIsolationLevel");
	}

	protected Scan buildScan() {
		try{
			if(startKeyGetter!=null){
				startPosition = (ExecIndexRow)startKeyGetter.invoke(activation);
				if(sameStartStopPosition){
					/*
					 * if the stop position is the same as the start position, we are
					 * right at the position where we should return values, and so we need to make sure that
					 * we only return values which match an equals filter. Otherwise, we'll need
					 * to scan between the start and stop keys and pull back the values which are greater than
					 * or equals to the start (e.g. leave startSearchOperator alone).
					 */
					stopPosition = startPosition;
					startSearchOperator= ScanController.NA; //ensure that we put in an EQUALS filter
				}
			}
			if(stopKeyGetter!=null){
				stopPosition = (ExecIndexRow)stopKeyGetter.invoke(activation);
			}
			if (scanQualifiersField != null)
				scanQualifiers = (Qualifier[][]) activation.getClass().getField(scanQualifiersField).get(activation);
			return Scans.setupScan(startPosition==null?null:startPosition.getRowArray(),startSearchOperator,
					stopPosition==null?null:stopPosition.getRowArray(),stopSearchOperator,
					scanQualifiers,conglomerate.getAscDescInfo(),accessedCols,Bytes.toBytes(transactionID));
		} catch (NoSuchFieldException e) {
			SpliceLogUtils.logAndThrowRuntime(LOG,e);
		} catch (IllegalAccessException e) {
			SpliceLogUtils.logAndThrowRuntime(LOG,e);
		} catch (StandardException e) {
			SpliceLogUtils.logAndThrowRuntime(LOG,e);
		} catch (IOException e) {
			SpliceLogUtils.logAndThrowRuntime(LOG,e);
		}
		return null;
	}
	@Override
	public FormatableBitSet getRootAccessedCols() {
		return this.accessedCols;
	}
}
