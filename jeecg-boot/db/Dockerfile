FROM registry.cn-hangzhou.aliyuncs.com/jeecgdocker/mysql:8.0.19

MAINTAINER jeecgos@163.com

ENV TZ=Asia/Shanghai

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY ./tables_nacos.sql /docker-entrypoint-initdb.d

COPY ./jeecgboot-mysql-5.7.sql /docker-entrypoint-initdb.d

COPY ./tables_xxl_job.sql /docker-entrypoint-initdb.d

# word-sync 独立库（db_wordsync 三表，PR #27 合入；仅在数据卷为空的首启执行）
COPY ./db_wordsync-init.sql /docker-entrypoint-initdb.d