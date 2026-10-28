package dev.ronova.pro;

/** Only fixed intents cross the wire; row targets are issued and resolved by the server. */
enum WeaponAction {
    REFRESH("刷新"),LINK_STOP("连带停 Mod"),TIME_STOP("全维度时停"),HOST("宿主保护"),DIAGNOSTICS("刷新诊断"),REPLAY("重放验证"),
    MOD_STOP("停止"),MOD_RETIRE("退役"),MOD_PROTECT("保护"),MOD_STATUS("详情"),
    FREEZE("冻结周围区域"),SPAWN_BAN("周围禁生成"),UNLOAD("卸载周围空闲区块"),SNAPSHOT("周边区块快照"),REGENERATE("重置本区块"),REGENERATE_TWO("重置周边 2 区块"),
    RULE_RESUME("解除"),WORLD_ROLLBACK("回溯"),WORLD_STATUS("详情"),WORLD_CANCEL("取消"),WORLD_RETRY("重试"),
    ENTITY_CLEAR("抹除"),ENTITY_PROTECT("保护 / 解除"),ENTITY_MOD_STOP("停其 Mod"),ENTITY_FIELDS("采字段"),ENTITY_FIELDS_CLEAR("清字段"),ENTITY_NO_HEAL("禁疗开关"),ENTITY_TELEPORT("传至身边"),
    FIELD_RESTORE("恢复字段"),FIELD_FORGET("释放"),FIELD_STATUS("详情"),
    OP_CANCEL("撤销"),OP_STATUS("详情"),OP_RECOVERY("恢复详情"),OP_HISTORY("历史"),OP_REQUIRED("全员必需"),OP_OPTIONAL("全员可选");
    final String title;
    WeaponAction(String title){this.title=title;}
    static WeaponAction at(int index){if(index<0||index>=values().length)throw new IllegalArgumentException("未知武器操作");return values()[index];}
}
record WeaponEntry(java.util.UUID id,String title,String state) {}