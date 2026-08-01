package com.mkz.data.influxdb;

import com.mkz.data.influxdb.anno.Insert;

import java.util.List;

public interface InfluxDBBaseMapper<T> {

    @Insert
    void insertOne(T entity);

    @Insert
    void insertBatch(List<T> entityList);
}
