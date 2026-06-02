import time
import threading

class SnowflakeGenerator:
    """
    雪花算法生成器
    结构: 1位符号位 + 41位时间戳 + 10位机器ID + 12位序列号
    """
    
    def __init__(self, machine_id: int = 1):
        self.machine_id = machine_id & 0x3FF  # 10位机器ID
        self.sequence = 0
        self.last_timestamp = -1
        self.lock = threading.Lock()
        
        # 位移常量
        self.TIMESTAMP_SHIFT = 22
        self.MACHINE_ID_SHIFT = 12
        self.SEQUENCE_MASK = 0xFFF
        
        # 起始时间戳 (2024-01-01 00:00:00 UTC)
        self.EPOCH = 1704067200000

    def _get_timestamp(self):
        """获取当前时间戳（毫秒）"""
        return int(time.time() * 1000)

    def _wait_next_millis(self, last_timestamp):
        """等待下一毫秒"""
        timestamp = self._get_timestamp()
        while timestamp <= last_timestamp:
            timestamp = self._get_timestamp()
        return timestamp

    def generate(self) -> int:
        """生成雪花ID"""
        with self.lock:
            timestamp = self._get_timestamp()
            
            if timestamp < self.last_timestamp:
                raise Exception(f"时钟回拨！拒绝生成ID，时间差: {self.last_timestamp - timestamp}ms")
            
            if timestamp == self.last_timestamp:
                self.sequence = (self.sequence + 1) & self.SEQUENCE_MASK
                if self.sequence == 0:
                    timestamp = self._wait_next_millis(self.last_timestamp)
            else:
                self.sequence = 0
            
            self.last_timestamp = timestamp
            
            # 组合ID
            snowflake_id = (
                ((timestamp - self.EPOCH) << self.TIMESTAMP_SHIFT)
                | (self.machine_id << self.MACHINE_ID_SHIFT)
                | self.sequence
            )
            
            return snowflake_id

    def generate_str(self) -> str:
        """生成雪花ID字符串"""
        return str(self.generate())

# 全局雪花生成器实例
snowflake_generator = SnowflakeGenerator()

def generate_snowflake_id() -> str:
    """生成雪花ID字符串"""
    return snowflake_generator.generate_str()