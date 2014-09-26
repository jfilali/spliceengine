package com.splicemachine.derby.impl.sql.execute.operations;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import com.splicemachine.derby.test.framework.SpliceDataWatcher;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.homeless.TestUtils;

/**
 * Created by jyuan on 7/30/14.
 */
public class WindowFunctionMultiIT extends SpliceUnitTest {
    public static final String CLASS_NAME = WindowFunctionMultiIT.class.getSimpleName().toUpperCase();

    protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher();
    protected static SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);

    private static String tableDef = "(empnum int, dept int, salary int, hiredate date)";
    public static final String TABLE_NAME = "EMPTAB";
    protected static SpliceTableWatcher spliceTableWatcher = new SpliceTableWatcher(TABLE_NAME,CLASS_NAME, tableDef);

    private static String[] EMPTAB_ROWS = {
        "20,1,75000,'2012-11-11'",
        "70,1,76000,'2012-04-03'",
        "60,1,78000,'2014-03-04'",
        "110,1,53000,'2010-03-20'",
        "50,1,52000,'2011-05-24'",
        "55,1,52000,'2011-10-15'",
        "10,1,50000,'2010-03-20'",
        "90,2,51000,'2012-04-03'",
        "40,2,52000,'2013-06-06'",
        "44,2,52000,'2013-12-20'",
        "49,2,53000,'2012-04-03'",
        "80,3,79000,'2013-04-24'",
        "100,3,55000,'2010-04-12'",
        "120,3,75000,'2012-04-03'",
        "30,3,84000,'2010-08-09'"
    };

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
                                            .around(spliceSchemaWatcher)
                                            .around(spliceTableWatcher)
                                            .around(new SpliceDataWatcher() {
            @Override
            protected void starting(Description description) {
                PreparedStatement ps;
                try {
                    for (String row : EMPTAB_ROWS) {
                        String sql = String.format("insert into %s values (%s)", spliceTableWatcher, row);
//                        System.out.println(sql);
                        ps = spliceClassWatcher.prepareStatement(sql);
                        ps.execute();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }}) ;

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher();

    @Test
    public void testRankDate() throws Exception {
        String[] hireDate = {"2010-03-20", "2010-03-20", "2011-05-24", "2011-10-15", "2012-04-03", "2012-11-11", "2014-03-04", "2012-04-03", "2012-04-03", "2013-06-06", "2013-12-20", "2010-04-12", "2010-08-09", "2012-04-03", "2013-04-24"};
        int[] dept = {1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3};
        int[] rankHire = {1, 1, 3, 4, 5, 6, 7, 1, 1, 3, 4, 1, 2, 3, 4};
        String sqlText =
            "SELECT hiredate, dept, rank() OVER (partition by dept ORDER BY hiredate) AS rankhire FROM %s";

        ResultSet rs = methodWatcher.executeQuery(
            String.format(sqlText, this.getTableReference(TABLE_NAME)));

        int i = 0;
        while (rs.next()) {
            Assert.assertEquals(hireDate[i],rs.getDate(1).toString());
            Assert.assertEquals(dept[i],rs.getInt(2));
            Assert.assertEquals(rankHire[i],rs.getInt(3));
            ++i;
        }
        rs.close();
    }

    @Test
    public void testMultiFunctionSameOverClause() throws Exception {
        // DB-1647 (partial; multiple functions with same over() work)
        int[] dept = {1 , 1 , 1 , 1, 1 , 1 , 1 , 2 , 2 , 2 , 2 , 3 , 3 , 3, 3};
        int[] denseRank = {1 , 2 , 3 , 4, 5 , 5 , 6 , 1 , 2 , 2 , 3 , 1 , 2 , 3, 4};
        int[] rank = {1, 2, 3, 4, 5, 5, 7, 1, 2, 2, 4, 1, 2, 3, 4};
        int[] rowNumber = {1, 2, 3, 4, 5, 6, 7, 1, 2, 3, 4, 1, 2, 3, 4};
        String sqlText =
            "SELECT empnum, dept, salary, DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary desc) AS DenseRank, RANK() OVER (PARTITION BY dept ORDER BY salary desc) AS Rank, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary desc) AS RowNumber FROM %s";

        ResultSet rs = methodWatcher.executeQuery(
            String.format(sqlText, this.getTableReference(TABLE_NAME)));

        int i = 0;
        while (rs.next()) {
            Assert.assertEquals(dept[i],rs.getInt(2));
            Assert.assertEquals(denseRank[i],rs.getInt(4));
            Assert.assertEquals(rank[i],rs.getInt(5));
            Assert.assertEquals(rowNumber[i],rs.getInt(6));
            ++i;
        }
        rs.close();
    }

    @Test
    public void testMultiFunctionSamePartitionDifferentOverBy() throws Exception {
        int[] denseRank = {1, 2, 3, 4, 5, 5, 6, 1, 2, 2, 3, 1, 2, 3, 4};
        int[] rank = {1, 2, 3, 4, 5, 5, 7, 1, 2, 2, 4, 1, 2, 3, 4};
        // DB-1647 (multiple functions with different over() do not work)
        String sqlText = "SELECT hiredate, dept, salary, DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary desc) AS DenseRank, RANK() OVER (PARTITION BY dept ORDER BY dept desc) AS rank FROM %s";

        ResultSet rs = methodWatcher.executeQuery(
            String.format(sqlText, this.getTableReference(TABLE_NAME)));

        int i = 0;
        while (rs.next()) {
            Assert.assertEquals(denseRank[i],rs.getInt(4));
            Assert.assertEquals(rank[i],rs.getInt(5));
            ++i;
        }
        rs.close();
    }

    @Test
    public void testMultiFunctionInQueryDiffAndSameOverClause() throws Exception {
        // DB-1647 (partial; multiple functions with same over() work)
        int[] dept = {1 , 1 , 1 , 1, 1 , 1 , 1 , 2 , 2 , 2 , 2 , 3 , 3 , 3, 3};
        int[] denseRank = {1 , 2 , 3 , 4, 5 , 5 , 6 , 1 , 2 , 2 , 3 , 1 , 2 , 3, 4};
        int[] rank = {1, 2, 3, 4, 5, 5, 7, 1, 2, 2, 4, 1, 2, 3, 4};
        int[] rowNumber = {1, 2, 3, 4, 5, 6, 7, 1, 2, 3, 4, 1, 2, 3, 4};
        String sqlText =
            "SELECT dept, salary, ROW_NUMBER() OVER (ORDER BY salary desc) AS RowNumber, RANK() OVER (PARTITION BY dept ORDER BY salary) AS Rank, DENSE_RANK() OVER (ORDER BY salary) AS DenseRank FROM %s";

        ResultSet rs = methodWatcher.executeQuery(
            String.format(sqlText, this.getTableReference(TABLE_NAME)));

        TestUtils.printResult(sqlText, rs, System.out);
        int i = 0;
        while (rs.next()) {
            Assert.assertEquals(dept[i],rs.getInt(2));
            Assert.assertEquals(denseRank[i],rs.getInt(4));
            Assert.assertEquals(rank[i],rs.getInt(5));
            Assert.assertEquals(rowNumber[i],rs.getInt(6));
            ++i;
        }
        rs.close();
    }
}