package io.crest.utils;

/**
 * 通用分页包装对象，携带分页记录和总量信息
 */
public class Pager<T> {
    /**
     * 返回给调用方的分页记录或列表型载荷
     */
    private T listObject;
    /**
     * 所有分页中的记录总数
     */
    private long itemCount;
    /**
     * 分页总页数
     */
    private long pageCount;

    /**
     * 为需要无参构造的框架创建空分页对象
     */
    public Pager() {
    }

    /**
     * 使用记录、记录总数和页数创建分页对象
     */
    public Pager(T listObject, long itemCount, long pageCount) {
        this.listObject = listObject;
        this.itemCount = itemCount;
        this.pageCount = pageCount;
    }

    /**
     * 返回分页总页数
     */
    public long getPageCount() {
        return pageCount;
    }

    /**
     * 设置分页总页数
     */
    public void setPageCount(long pageCount) {
        this.pageCount = pageCount;
    }


    /**
     * 返回记录总数
     */
    public long getItemCount() {
        return itemCount;
    }

    /**
     * 设置记录总数
     */
    public void setItemCount(long itemCount) {
        this.itemCount = itemCount;
    }

    /**
     * 返回分页记录载荷
     */
    public T getListObject() {
        return listObject;
    }

    /**
     * 设置分页记录载荷
     */
    public void setListObject(T listObject) {
        this.listObject = listObject;
    }
}
