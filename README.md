# `ShardSim`文档

## branch

`main`：网络配置、交易处理逻辑、配置文件都为空白，实现新模型请基于此branch新建一个branch

`example_singleNode`：一个极为简单的使用样例，配置文件`sample.properties`位于项目根目录下

## 配置文件

通过在运行时增加一个参数，指定配置文件的路径，格式为properties

### 必要的配置

- `nodeNumber`：（整数）用于初始化网络中的节点
- `limitBandwidth`：（true/false）是否根据带宽带宽限制阻塞消息
- `externalLantency`：（整数）外部节点到任意网络中节点的双向延迟
- `transactions`：（JSON）中
  - `number`：（整数）生成的交易数量
  - `interval`：（整数）交易生成的间隔

### 自定义的配置

- `model`：（JSON）在`MyNetwork`类的构造函数中被接收，用于配置网络连接和模型参数
- `transactions`：（JSON）会被`TxGenerator`的构造函数接收，除了上述的两个必要域，可以自定义其他参数用于配置交易列表。`TxGenerator`的默认实现，需要使用以下参数
  - `DSAttackRate`：（浮点数）产生双花交易的概率，注：双花交易成对产生，不会影响有效交易的数量，但会使系统处理的交易数大于`transactions.number`
  - `inputNum`：（JSON）输入数量的分布，格式为`{概率:取值, 概率:取值, ...}`
  - `outputNum`：（JSON）输出数量的分布，格式为`{概率:取值, 概率:取值, ...}`

## `MyNetwork`

用于初始化模型，以及配置网络连接

调用`addEdge(from,to,latency,bandwidth)`来新增一条边

在配置`limitBandwidth=false`时，可以使用`addEdge(from,to,latency)`

**不要**删除调用super构造器的代码

## `ModelData`

给模型提供一个地方来存放静态数据

如果需要每个节点一套数据，可以开个数组

在调用`TxGenerator`生成正常交易前，框架会创建10000个`coinbase`交易以初始化UTXO池。为了让模型同步这些合法的UTXO，框架会对每个初始UTXO调用`addInitUTXO`，请妥善实现该函数

## `TxProcessing`

实现了`EventHandler`接口

节点从用户处接收到交易时，会调用该类的`run`方法。您需要在这里妥善实现该节点的行为（包括转发交易），具体的可用操作在该类的注释中可以找到

## `TxGenerator`

该类已经有一个默认实现，所需参数已经在“配置文件”一节给出。对每个交易生成事件，默认实现或生成一个合法交易，或生成一对只能有一个合法的双花交易。之后，生成的交易被随机提交到一个节点上。

由于模型可能需要应对不同场景，默认的交易的生成机制或许不能满足所有需求。您可以重写该类来定义所需的交易生成逻辑

## 单位

时间和数据量的单位可以自取，但请务必保证所有地方的单位相同，框架不会做任何单位转换。如选用毫秒作为时间单位，那么吞吐量数值的含义就是每毫秒交易数

