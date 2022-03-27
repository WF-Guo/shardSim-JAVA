# `ShardSim`

## branch

`main`:网络配置、交易处理逻辑、配置文件都为空白，实现新模型请基于此branch新建一个branch

`example_singleNode`：一个极为简单的使用样例，配置文件`sample.json`位于项目根目录下

## 配置文件

通过在运行时增加一个参数，指定配置文件的路径，格式为JSON

### 必要的配置

- `nodeNumber`：（整数）用于初始化网络中的节点
- `limitBandwidth`：（true/false）是否根据带宽带宽限制阻塞消息
- `externalLantency`：（整数）用户等外部网络实体到任意网络中节点的双向延迟（注：因为用户的网络位置不固定，模拟节点和用户之间的带宽堵塞是没必要的）
- `initUTXO`：（整数）系统启动时生成的UTXO数量
- `transactions`：（JSON）中
  - `number`：（整数）生成的交易数量
  - `interval`：（整数）交易生成的间隔

### 自定义的配置

- `model`：（JSON）在`MyNetwork`类的构造函数中被接收，用于配置网络连接和模型参数
- `transactions`：（JSON）会被`TxGeneration`的构造函数接收，除了上述的两个必要域，可以自定义其他参数用于配置交易列表。`TxGeneration`的默认实现，需要使用以下参数
  - `DSAttackRate`：（浮点数）产生双花交易的概率，注：双花交易成对产生，不会影响有效交易的数量，但会使系统处理的交易数大于`transactions.number`
  - `inputNum`：（JSON）输入数量的分布，格式为`{概率:取值, 概率:取值, ...}`
  - `outputNum`：（JSON）输出数量的分布，格式为`{概率:取值, 概率:取值, ...}`

## `MyNetwork`

用于初始化模型，以及配置网络拓扑结构

调用`addEdge(from,to,latency,bandwidth)`来新增一条单向边

在配置`limitBandwidth=false`时，可以使用`addEdge(from,to,latency)`

**不要**删除调用super构造器的代码

## `ModelData`

给模型提供一个地方来存放静态数据，以及实现可复用的静态方法

如果需要每个节点一套数据，可以开个数组

在调用`TxGeneration`生成正常交易前，框架会按配置创建`initUTXO`个`coinbase`交易以初始化UTXO池。为了让模型同步这些合法的UTXO，框架会对每个初始UTXO调用`addInitUTXO`，请妥善实现该函数

## `TxProcessing`

实现了`NodeAction`接口

节点从用户处接收到交易时，会调用该类的`runOn`方法。您需要在这里妥善实现该节点的行为（包括转发交易），具体的可用操作包括：

1. `currentNode.getId()` 返回当前节点的ID。根据配置中的节点数`nodeNumber`，节点的ID范围为`[0,nodeNumer-1]`。可以根据节点ID索引`ModelData`中的静态数组，得到节点数据；也可以通过条件语句，根据节点ID分类节点（诚实/恶意、不同分片），实现不同的行为
2. 当用户有足够把握确认一笔交易时，调用`TxStat.confirm(tid)`来统计该交易的延迟。重复提交一笔交易时，不会报错，但只有第一次的提交时间会被用于计算。若冲突的一对交易先后被提交，会抛出异常
3. 实现`NodeAction`接口来规定节点未来的动作，该动作可能由下面几条方式触发。实现`NodeAction`接口的类可以通过有参构造函数来为其对象增加属性，来实现参数不同的相似行为
4. 若对象实现了`NodeAction`，可以调用其成员方法`xxx.runOn(currentNode)`来立刻于本地执行其他动作，也可以通过`EventDriver.insertEvent(time,currentNode,action)`来计划于时刻`time`执行。可以通过`EventDriver.getCurrentTime()`来获取当前时间
5. 模拟执行耗时的任务时，调用`currentNode.stayBusy(time,nextAction)`来将当前节点锁定一段时间，并指定此任务结束后的行为。可以将多个连续的任务打包为一次调用，直到提交交易或进行通信。同一个`NodeAction`中禁止多次调用`stayBusy`，推荐在调用`stayBusy`后立即让当前的`NodeAction`终止
6. 模拟节点间通信时，使用`currentNode.sendMessage(receiver,receivingAction,size)`，来指定接收信息的节点，接收后的行为，以及传输数据的大小（这会影响网络拥堵程度）。目标节点接收到信息的时间由网络延迟决定，不可以手动指定
7. 外部网络实体（如用户运行的轻节点）也可能需要参与进交易处理中。与外部实体通信时不可以使用`sendMessage`，系统内节点通过`currentNode.sendOut(receivingAction)`可以将消息发给外部实体，外部实体上运行的`Action`中可以通过`currentNode.sendIn(receiver,receivingAction)`将消息发给指定内部节点
8. 外部实体也可以使用`currentNode.stayBusy(time,nextAction)`，但这将起到将`nextAction`延迟触发的效果，而不会阻塞其他需要外部实体处理的事件，因为在现实网络中，通常不同交易由不同的用户发出，不同用户对自己交易的处理完全可以并行。
9. 如果需要创建`coinbase`交易，创建一个无输入的交易并调用`TxStat.confirm`，即可将其输出加入交易生成器所用的UTXO集合。这种交易不会计入延迟和吞吐量的计算。

## `TxGeneration`

该类已经有一个默认实现，所需参数已经在“配置文件”一节给出。对每个交易生成事件，默认实现或生成一个合法交易，或生成一对只能有一个合法的双花交易。之后，生成的交易被随机提交到一个节点上

由于模型可能需要应对不同场景，默认的交易的生成机制或许不能满足所有需求。您可以重写该类来定义所需的交易生成逻辑。为自定义提供的功能如下：

1. 在`TxStat`中，维护了用户视角的UTXO集合（与`TxStat.confirm(tid)`一致），可以用`TxStat.utxoSize()`获取剩余UTXO的数量，用`TxStat.getRandomUTXO()`来从集合中随机取出一个UTXO
2. 用`new TxInfo()`来生成一笔空的交易，其交易ID是自增的，不需要手动分配。`inputs`域已经初始化为一个空的列表，需要构造`TxInput`放入其中。`outputNum`也需要手动赋值
3. 使用`TxStat.submit(tx)`来开始统计这笔交易的延迟。这也会将交易的输入和交易绑定，用于处理冲突交易。当UTXO所绑定的所有交易都与已经确认的交易冲突时，UTXO会被自动放回集合。当前的实现中，若一笔交易已经`confirm`后，冲突的另一笔交易才`submit`，是不会触发异常的，还请不要`submit`和已经确认的交易冲突的交易。

## 单位

时间和数据量的单位可以自取，但请务必保证所有地方的单位相同，框架不会做任何单位转换。如选用毫秒作为时间单位，那么吞吐量数值的含义就是每毫秒交易数

