package edu.whu.tmdb.query.operations.utils;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.whu.tmdb.query.operations.Exception.ErrorList;
import com.alibaba.fastjson2.JSON;
import edu.whu.tmdb.query.operations.Exception.TMDBException;
import edu.whu.tmdb.storage.memory.MemManager;
import edu.whu.tmdb.storage.memory.SystemTable.*;
import edu.whu.tmdb.storage.memory.Tuple;
import edu.whu.tmdb.storage.memory.TupleList;
import edu.whu.tmdb.storage.utils.K;
import edu.whu.tmdb.storage.utils.V;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemConnect {
    // 进行内存操作的一些一些方法和数据
    private static Logger logger = LoggerFactory.getLogger(MemConnect.class);
    private MemManager memManager;

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // 1. 私有静态变量，用于保存MemConnect的单一实例
    private static volatile MemConnect instance = null;

    // 2. 私有构造函数，确保不能从类外部实例化
    private MemConnect() {
        // 防止通过反射创建多个实例
        if (instance != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }

    // 3. 提供一个全局访问点
    public static MemConnect getInstance(MemManager mem) {
        // 双重检查锁定模式
        if (instance == null) { // 第一次检查
            synchronized (MemConnect.class) {
                if (instance == null) { // 第二次检查
                    instance = new MemConnect(mem);
                }
            }
        }
        return instance;
    }

    private MemConnect(MemManager mem) { this.memManager = mem; }

    //获取tuple
    public Tuple GetTuple(int id) {
        rwLock.readLock().lock(); // 获取读锁
        Tuple t = null;
        try {
            Object searchResult = this.memManager.search(new K("t" + id));
            if (searchResult == null)
                t= null;
            if (searchResult instanceof Tuple)
                t = (Tuple) searchResult;
            else if (searchResult instanceof V)
                t= JSON.parseObject(((V) searchResult).valueString, Tuple.class);
            if (t.delete)
                t= null;
        }finally {
            rwLock.readLock().unlock();
            return t;
        }
    }

    //插入tuple
    public void InsertTuple(Tuple tuple) {
        rwLock.writeLock().lock(); // 获取写锁
        try {
            this.memManager.add(tuple);
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    //删除tuple
    public void DeleteTuple(int id) {
        rwLock.writeLock().lock();
        try {
            if (id >= 0) {
                Tuple tuple = new Tuple();
                tuple.tupleId = id;
                tuple.delete = true;
                memManager.add(tuple);
            }
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    // 更新tuple
    public void UpateTuple(Tuple tuple, int tupleId) {
        rwLock.writeLock().lock();
        try {
            tuple.tupleId = tupleId;
            this.memManager.add(tuple);
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 给定表名(类名), 获取表在classTable中的id值
     * @param tableName 表名(类名)
     * @return 给定表名(类名)所对应的class id
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public int getClassId(String tableName) throws TMDBException {
        // TODO
        // 不存在时抛出异常
        int classId = -1;
        for (ClassTableItem item : getClassTableList()) {
            if (item.classname.equals(tableName)) {
                classId = item.classid;
                return classId;
                //break;
            }
        }
        //if (classId == -1) {
        throw new TMDBException(ErrorList.CLASS_NAME_DOES_NOT_EXIST, tableName);
        //}
        //return classId;
    }

    /**
     * 给定表名(类名), 获取表在classTable中的属性名列表
     * @param tableName 表名(类名)
     * @return 给定表名(类名)所对应的属性名列表
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public List<String> getColumns(String tableName) throws TMDBException {
        // TODO
        // 不存在时抛出异常
        List<String> columns = new ArrayList<>();
        //int classId = getClassId(tableName);
        for (ClassTableItem item : MemConnect.getClassTableList()) {
            if (item.classname.equals(tableName)) {
                columns.add(item.attrname); // 使用 attrname 属性获取属性名
            }
        }
        if (columns.isEmpty()) {
            throw new TMDBException(ErrorList.CLASS_NAME_DOES_NOT_EXIST, tableName);
        }
        return columns;
    }

    /**
     * 给定表名(类名), 获取表在中属性的数量
     * @param tableName 表名(类名)
     * @return 给定表名(类名)所具有的属性数量(attrNum)
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public int getClassAttrnum(String tableName) throws TMDBException {
        // TODO
        // 不存在时抛出异常
        for (ClassTableItem item : getClassTableList()) {
            if (item.classname.equals(tableName)) {
                return item.attrnum;
            }
        }
        throw new TMDBException(ErrorList.CLASS_NAME_DOES_NOT_EXIST, tableName);
    }

    /**
     * 给定表id(类id), 获取表在classTable中的属性数量
     * @param classId 表名(类名)
     * @return 给定表名(类名)所具有的属性数量(attrNum)
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public int getClassAttrnum(int classId) throws TMDBException {
        // TODO
        // 不存在时抛出异常
        for (ClassTableItem item : getClassTableList()) {
            if (item.classid == classId) {
                return item.attrnum;
            }
        }
        throw new TMDBException(ErrorList.CLASS_ID_DOES_NOT_EXIST, "getClassAttrnum"+classId);
    }

    /**
     * 用于获取插入位置对应的属性id列表 (attrid)
     * @param classId insert对应的表id/类id
     * @param columns insert对应的属性名称列表
     * @return 属性名列表对应的attrid列表
     */
    public int[] getAttridList(int classId, List<String> columns) throws TMDBException {
        // TODO
        // 不存在时抛出异常
        List<Integer> attridList = new ArrayList<>();
        boolean classFound = false;

        for (ClassTableItem item : getClassTableList()) {
            if (item.classid == classId) {
                classFound = true;
                if (columns.contains(item.attrname)) {  // 只添加需要的属性名对应的 attrid
                    attridList.add(item.attrid);
                }
            }
        }

        if (!classFound) {
            throw new TMDBException(ErrorList.CLASS_ID_DOES_NOT_EXIST, "getAttridList"+String.valueOf(classId));
        }

        int[] result = new int[attridList.size()];
        for (int i = 0; i < attridList.size(); i++) {
            result[i] = attridList.get(i);
        }
        return result;
    }

    /**
     * 给定classId和attrName，返回属性的attrid
     * @param classId 类id号
     * @param attrName 属性名称
     * @return 属性对应的id
     */
    public int getAttrid(int classId, String attrName) throws TMDBException {
        // TODO
        // 不存在时抛出异常
        int classMatch=0;
        for (ClassTableItem item : getClassTableList()) {
            if(item.classid == classId){
                classMatch=1;
            }
            if (item.classid == classId && item.attrname.equals(attrName)) {
                return item.attrid;
            }
        }
        if(classMatch==1)
        {
            throw new TMDBException(ErrorList.CLASS_ID_DOES_NOT_EXIST, "getAttrid"+String.valueOf(classId));
        }
        else
        {
            throw new TMDBException(ErrorList.COLUMN_NAME_DOES_NOT_EXIST, "class exists but attrname not found:"+attrName);
        }
        // 如果没有找到匹配的ClassTableItem
        //throw new TMDBException(ErrorList.ATTRIBUTE_DOES_NOT_EXIST, "Class ID: " + classId + ", Attr Name: " + attrName);
    }

    /**
     * 给定表名，获取表下的所有元组
     * @param fromItem 表名
     * @return 查询语句中，该表之下所具有的所有元组
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public TupleList getTupleList(FromItem fromItem) throws TMDBException {
        int classId = getClassId(((Table) fromItem).getName());
        TupleList tupleList = new TupleList();
        for (ObjectTableItem item : getObjectTableList()) {
            if (item.classid != classId) {
                continue;
            }
            Tuple tuple = GetTuple(item.tupleid);
            if (tuple != null && !tuple.delete) {
                tuple.setTupleId(item.tupleid);
                tupleList.addTuple(tuple);
            }
        }
        return tupleList;
    }

    /**
     * 给定表名，获取表名class table的副本
     * @param fromItem 表名
     * @return 表名对应的class table副本
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public ArrayList<ClassTableItem> copyClassTableList(FromItem fromItem) throws TMDBException{
        ArrayList<ClassTableItem> classTableList = new ArrayList<>();
        for (ClassTableItem item : getClassTableList()){
            if (item.classname.equals(((Table)fromItem).getName())){
                // 硬拷贝，不然后续操作会影响原始信息
                ClassTableItem classTableItem = item.getCopy();
                if (fromItem.getAlias() != null) {
                    classTableItem.alias = fromItem.getAlias().getName();
                }
                classTableList.add(classTableItem);
            }
        }
        if (classTableList.isEmpty()) {
            throw new TMDBException(ErrorList.CLASS_NAME_DOES_NOT_EXIST, ((Table)fromItem).getName());
        }
        return classTableList;
    }

    /**
     * 给定表名，返回该表是否存在，存在返回true
     * @param tableName 表名
     * @return 存在返回true，否则返回false
     */
    public boolean classExist(String tableName) {
        // TODO
        for (ClassTableItem item : getClassTableList()) {
            if (item.classname.equals(tableName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 给定表名和属性名，返回该属性是否存在，存在返回true
     * @param tableName 表名
     * @param columnName 属性名
     * @return 存在返回true，否则返回false
     */
    public boolean columnExist(String tableName, String columnName) throws TMDBException {
        // TODO
        for (ClassTableItem item : getClassTableList()) {
            if (item.classname.equals(tableName)) {
                if (item.attrname.equals(columnName)) {
                    return true;  // 属性名也匹配，说明列存在
                }
            }
        }
        return false;

    }

    /**
     * 给定class id, 获取该源类对应的所有代理类id
     * @param classId 源类的class id
     * @return 该class id对应的所有代理类id
     * @throws TMDBException 不存在给定表名的表，抛出异常
     */
    public ArrayList<Integer> getDeputyIdList(int classId) throws TMDBException {
        // TODO
        // 不存在时抛出异常
//        ArrayList<Integer> attributeIds = new ArrayList<>();
//        boolean classFound = false;
//
//        for (ClassTableItem item : getClassTableList()) {
//            if (item.classid == classId) {
//                classFound = true;
//                attributeIds.add(item.attrid);
//            }
//        }
//
//        if (!classFound) {
//
//        }
//
//        return attributeIds;
        ArrayList<Integer> deputyIdList = new ArrayList<>();
        for (BiPointerTableItem item : getBiPointerTableList()) {   //源类id列表
//            if(item.deputyobjectid == classId){         //代理类id=对应=源类id
//                deputyIdList.add(item.deputyobjectid);
//            }
            if (item.classid == classId && !deputyIdList.contains(item.deputyid)) {
                deputyIdList.add(item.deputyid);
            }
        }
//        if (deputyIdList.isEmpty()) {
//            throw new TMDBException(ErrorList.CLASS_ID_DOES_NOT_EXIST, "QQQQQQ" + classId);
//        }
        return deputyIdList;
    }

    public boolean Condition(String attrtype, Tuple tuple, int attrid, String value1) {
        String value = value1.replace("\"", "");
        switch (attrtype) {
            case "int":
                int value_int = Integer.parseInt(value);
                if (Integer.parseInt((String) tuple.tuple[attrid]) == value_int)
                    return true;
                break;
            case "char":
                String value_string = value;
                if (tuple.tuple[attrid].equals(value_string))
                    return true;
                break;

        }
        return false;
    }

    public void SaveAll() { memManager.saveAll(); }

    public void reload() {
        try {
            memManager.loadClassTable();
            memManager.loadDeputyTable();
            memManager.loadBiPointerTable();
            memManager.loadSwitchingTable();
        }catch (IOException e){
            logger.error(e.getMessage());
        }
    }

    public static class OandB {
        public List<ObjectTableItem> o = new ArrayList<>();
        public List<BiPointerTableItem> b = new ArrayList<>();

        public OandB() {
        }

        public OandB(MemConnect.OandB oandB) {
            this.o = oandB.o;
            this.b = oandB.b;
        }

        public OandB(List<ObjectTableItem> o, List<BiPointerTableItem> b) {
            this.o = o;
            this.b = b;
        }
    }

    // 获取系统表
    public static ObjectTable getObjectTable() { return MemManager.objectTable; }

    public static ClassTable getClassTable() { return MemManager.classTable; }

    public static DeputyTable getDeputyTable() { return MemManager.deputyTable; }

    public static BiPointerTable getBiPointerTable() { return MemManager.biPointerTable; }

    public static SwitchingTable getSwitchingTable() { return MemManager.switchingTable; }

    // 获取系统表表项
    public static List<ObjectTableItem> getObjectTableList() { return MemManager.objectTable.objectTableList; }

    public static List<ClassTableItem> getClassTableList() { return MemManager.classTable.classTableList; }

    public static List<DeputyTableItem> getDeputyTableList() { return MemManager.deputyTable.deputyTableList; }

    public static List<BiPointerTableItem> getBiPointerTableList() { return MemManager.biPointerTable.biPointerTableList; }

    public static List<SwitchingTableItem> getSwitchingTableList() { return MemManager.switchingTable.switchingTableList; }
}
