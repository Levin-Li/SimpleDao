### 简介
   
   SimpleDao是一个使用注解生成SQL语句和参数的小组件，目前组件依赖Spring并结合JPA，如果非JPA环境项目需要使用，可以使用  genFinalStatement()、 genFinalParamList() 方法以来获取SQL语句和参数。
   
   在项目中应用本组件能大量减少语句的编写和SQL参数的处理。组件支持Where子句、标量统计函数和Group By子句、Having子句、Order By子句、Select子句、Update Set子句、子查询等。

   SimpleDao的目标

   1、减少直接编写查询语句（SQL或JPQL），提升开发效率，减少代码量，降低编码能力要求。

   2、简化DAO层，或是直接放弃具体的Domain对象DAO层，使用支持泛型通用的Dao。

   开发思路

   通过在DTO对象中加入自定义注解自动生成查询语句。

   DAO层的在Web应用中的位置：

   client request --> spring mvc controller --> DTO(数据传输对象) --> service(含cache)  --> dao(手动编写查询语句) --> (JDBC,MyBatis,JPA)

   SimpleDao优化后的过程：

   client request --> spring mvc controller --> DTO(数据传输对象) --> service(含cache)  --> SimpleDao(使用DTO自动生成查询语句) --> (JDBC,MyBatis,JPA)
   
    
   
### 1 快速上手
   
  要求 Spring boot 2.0.5 以上的环境。
   
#### 1.1 引入依赖

           <repositories>
       
               <repository>
                   <id>jitpack.io</id>
                   <url>https://jitpack.io</url>
               </repository>
       
           </repositories>

        <dependency>
             <groupId>com.github.Levin-Li</groupId>
            <artifactId>simple-dao-jpa-starter</artifactId>
            <version>3.0.0-SNAPSHOT</version>
        </dependency>
        
##### 1.2 定义DTO及注解

     /**
      * 数据传输对象(兼查询对象，通过注解产生SQL语句)
      */
    @Data
    @TargetOption(entityClass = TestEntity.class)
    public class TestEntityStatDto {
           
        @Min
        Long minScore; //当minScore字段名在实体对象中不存在时，会尝试自动去除注解的名字 minScore -> score
        
        @Max
        Long maxScore;
    
        @Avg
        Long avgScore;
    
        @Count
        Long countScore;
    
        @GroupBy
        @NotIn
        String[] state = {"A", "B", "C"}; 
    
        @Contains
        String name = "test"; 
    }
   
  
  测试用例类 [TestEntityStatDto](./simple-dao-jpa-starter/src/test/java/com/levin/commons/dao/dto/TestEntityStatDto.java) 
  
  其它测试用例参考：[Dto注解](./simple-dao-jpa-starter/src/test/java/com/levin/commons/dao/dto) 
   

##### 1.3 配置JPA实体扫描 & 执行查询

  1、在boot启动类上配置实体扫描注解
   
      @EntityScan({"com.levin.commons.dao","com.xxx.xxx.entities"})
  
        
  2、执行查询
  
      @Autowired
      JpaDao jpaDao;
      
      jpaDao.findByQueryObj(TestEntityStatDto.class,new TestEntityStatDto());

   以上代码将生成并执行以下SQL：

       Select 
       Min( score ) , 
       Max( score ) , 
       Avg( score ) , 
       Count( 1 ) , 
       state  
       From com.levin.commons.dao.domain.support.TestEntity     
       Where 
       state NOT IN (  ?1 , ?2 , ?3  ) 
       AND name LIKE '%' ||  ?4  || '%'  
       Group By  state
  

###  2、组件使用方式

#### 2.1 直接使用通用Dao（推荐）

##### 2.1.1 使用JpaDao
   在服务层代码中通过Spring注入JpaDao实例，通过JpaDao动态创建。

   使用示例：

      @Autowired
      JpaDao jpaDao;

      SelectDao selectDao = jpaDao.selectFrom("t_table_name","alias");

      List queryResult = selectDao.appendByQueryObj(new UserStatDTO()).find();

##### 2.1.1 使用SelectDao 、UpdateDao、DeleteDao

    //查询DAO
    SelectDao dao = jpaDao.selectFrom(Group.class);
    dao.find()

    //更新DAO
    UpdateDao dao = jpaDao.updateTo(Group.class);
    dao.update()

    //删除DAO
    DeleteDao dao = jpaDao.deleteFrom(Group.class)
     dao.delete()

#### 2.2 自定义DAO接口或是DAO类(不推荐，建议在服务类中使用JpaDao)

#### 2.2.1 自定义DAO接口

   接口DAO案例：

    //DAO 自动扫描注解
    @EntityRepository("用户DAO")

    //DAO默认操作目标注解
    @TargetEntity(entityClass = User.class, alias = "u"
            , fixedCondition = "u.enable = true", defaultOrderBy = "u.orderCode desc")

    public interface UserDao {

        List<User> find(@Eq Long id, @Like String name,
                        @Gt Integer score, Paging paging);

        @QueryRequest(joinFetchSetAttrs = {"group"})
        User findOne(@Eq Long id, @Like String name,
                     @Eq String category, Paging paging);

        @UpdateRequest
        int update(@Eq Long id, @UpdateColumn String name);

        @DeleteRequest
        int delete(@OR @Eq Long id, String name);

    }

接口DAO定义好后，直接在需要的服务类中直接通过Spring注入

         @Autowired
         UserDao userDao;

         userDao.delete(...)


   **特别说明：**
   需要在JDK1.8中编译，并增加编译参数：-parameters ，保留方法的参数名称。

      javac -parameters


#### 2.2.2 自定义DAO类

   DAO抽象类案例：

    //DAO 自动扫描注解
    @EntityRepository("组DAO")

    //DAO默认操作目标注解
    @TargetEntity(entityClass = Group.class, fixedCondition = "enable = true", defaultOrderBy = "orderCode desc")
    public abstract class GroupDao {

        @Autowired
        private JpaDao jpaDao;

        @QueryRequest
        public Group findOne(@OR @Eq Long id, @Like String name,
                             @Eq String category, Paging paging) {

            //获取查询结果的关键点：RepositoryFactoryBean.getProxyInvokeResult()
            Group result = RepositoryFactoryBean.getProxyInvokeResult();

            System.out.println(result);

            return (Group) result;
        }

        @QueryRequest
        public List<Group> find(@OR @Eq Long id, @Like String name,
                                @Eq String category, Paging paging) {

            List<Group> groups = RepositoryFactoryBean.getProxyInvokeResult();

            //...处理其它逻辑

            System.out.println(groups);

            return groups;
        }

        @UpdateRequest
        public int update(@Eq Long id, @UpdateColumn String name) {

            Integer r = RepositoryFactoryBean.getProxyInvokeResult();

            //...处理其它逻辑

            return r != null ? r : 0;
        }

        //没有注解方法将无效，如果调用RepositoryFactoryBean.getProxyInvokeResult(); 将会生产异常
        public Object noAnnoMethod(@Eq Long id, @UpdateColumn String name) {

            Object r = RepositoryFactoryBean.getProxyInvokeResult();

            return r;
        }

        @QueryRequest
        public Group findOneAndRepeatGetResult(@OR @Eq Long id, @Like String name,
                                               @Eq String category, Paging paging) {

            Object result = RepositoryFactoryBean.getProxyInvokeResult();

            System.out.println(result);

            RepositoryFactoryBean.getProxyInvokeResult();

            return (Group) result;
        }
    }

抽象DAO定义好后，直接在需要的服务类中直接通过Spring注入

    @Autowired
    GroupDao groupDao;

    //使用Dao
     groupDao.find() ...
           
#### 2.2.2 设置扫描包名 & 启用扫描  

     //设置 EntityRepository 注解的扫描范围        
     @ProxyBeanScan(scanType = EntityRepository.class, factoryBeanClass = RepositoryFactoryBean.class
                     , basePackages = {"com. levin. commons . dao.."})
                     
     //启用组件扫描                
     @EnableProxyBean(registerTypes = EntityRepository.class)                


### 3 组件接口及注解

#### 3.1 Dao接口

*    [SelectDao](./simple-dao-core/src/main/java/com/levin/commons/dao/SelectDao.java)

*    [UpdateDao](./simple-dao-core/src/main/java/com/levin/commons/dao/UpdateDao.java)

*    [DeleteDao](./simple-dao-core/src/main/java/com/levin/commons/dao/DeleteDao.java)

*    [SimpleDao](./simple-dao-core/src/main/java/com/levin/commons/dao/SimpleDao.java)
       
*    [JpaDao](./simple-dao-jpa/src/main/java/com/levin/commons/dao/JpaDao.java)
       

#### 3.2 注解的语句生成规则

  操作枚举类：[com.levin.commons.dao.annotation.Op](./simple-dao-annotations/src/main/java/com/levin/commons/dao/annotation/Op.java)，定义了常见的 sql 表达式。
  

  语句表达式生成规则： surroundPrefix + op.gen( funcs(fieldName), funcs([ paramExpr(优先) or 参数占位符 ])) +  surroundSuffix


### 4、DTO 查询注解
    
   查询注解 主要再 com.levin.commons.dao.annotation 包中，包括常见的 SQL 操作符。 

   DTO类字段定义示例：

       @Desc("店铺id")
       private Long storeId;

       @Desc("店铺名称")
       @Eq
       private String storeName;

       @Desc("店铺所在区域")
       private String storeArea;

       @Desc("店铺状态")
       private StoreStatus storeStatus;

       @Desc("店铺库存预警")
       @Ignore
       private Boolean storageAlarm;

       @Desc("商品分类id")
       @Like
       private String classId;


### 5、统计查询

   统计注解在com.levin.commons.dao.annotation.stat 包中，主要包括以下注解：
   
        @Avg
        @Max
        @Min
        @Sum
        @Count
        @GroupBy
        
   
   统计注解 有一个 havingOp 属性，用来表示 Having 查询字句，如：
     
         @Avg(havingOp = Op.Gt)
         Long avgScore = 10L;
         
         @GroupBy
         @Gt 
         int month = 5;
   
   意思注解将产生语句： select month , AVG(score) from XXX where month > 5 having AVG(score) > 10 
   
   Dao 支持多表统计，如下例子：
   
       
       jpaDao.selectFrom(Group.class, "g")
                       .join("left join " + User.class.getName() + " u on g.id = u.group.id")
                       .join("left join " + Task.class.getName() + " t on u.id = t.user.id")
                       .count("1")
                       .avg("t.score")
                       .avg("u.score")
                       .avg("g.score")
                       .sum("t.score")
                       .groupByAsAnno(E_Group.name)
                       .find();
      

### 6、指定字段的查询和数据更新

  选择查询注解：

          @Select
          String field;

  产生的语句：

      select field from ...


  更新注解：

         @Update
         protected Date lastUpdateTime = new Date();

  产生的语句

         set lastUpdateTime = ?



### 7、逻辑嵌套查询(用于实现复杂的查询条件)

   逻辑注解支持

     @AND
        @OR
        @END
     @END

     #逻辑注解可以嵌套使用，当有一个节条件不成立时，嵌套的所有子条件都将被忽略

    //以下为代码片段

    @AND(condition = "#_val == true")
    protected Boolean editable = true;

    @Lt
    @OR(condition = "#_val!=null")
    protected Date createTime = new Date();

    @Between("score")
    @End
    protected Integer[] scores = new Integer[]{200, 100};

    @Like
    @End
    protected String description = "keywords";

  以上注解将生成如下语句：

      editable = ?
      and ( createTime < ? or  scores Between ? and ? )
      and description like ?


  Dao 方法支持
    
     //逻辑嵌套
     jpaDao.selectFrom("table").and().or().and().end().end().end();

### 8、子查询
 

#### 8.1 手动指定子查询语句(paramExpr属性)

          @Ignore
          @SelectColumn("score")
          UserStatDTO selectSubQueryDTO = new UserStatDTO();

          @Ignore
          @SelectColumn(value = "score", paramExpr = "select 3000 from xxx.tab t where u.id = t.id")
          Map param = new HashMap();

          //子查询，并使用命名参数，命名参数从Map变量中取
          @NotExists(paramExpr = "select name from xxx.tab t where u.id = t.id and t.score > :minScore")
          Map<String, Object> namedParams = new HashMap<>();


          //子查询，子查询将从subQueryDTO查询对象中生成
          @NotExists
          UserStatDTO statDTO = new UserStatDTO();

          //子查询产生
          @Gt("score")
          UserStatDTO whereSubQueryDTO = new UserStatDTO();


#### 8.2  使用嵌套查询对象

        //子查询，子查询将从subQueryDTO查询对象中生成
        @NotExists
        DTO subQueryDTO = new DTO();

        //In注解，将生产子查询
        @In("status")
        DTO subQueryDTO = new DTO();


### 9、排序(OrderBy注解)

   排序使用OrderBy注解，OrderBy支持字段和参数。

    @Contains
    @OrderBy
    String name = "test";


    @OrderByList(
            {
                    @OrderBy(E_User.createTime),
                    @OrderBy(value = E_User.area, order = 5,type = OrderBy.Type.Asc),
                    @OrderBy(condition = C.NOT_NULL)
            }
    )
    String orderCode ="1111";

   以上将生成OrderBy将生成如下语句：

    Order By  area Asc , name Desc , createTime Desc , orderCode Desc


### 10、使用注意事项

#### 10.1  DTO 查询对象字段无注解的情况


##### 10.1.1 基本类型字段无注解
  基本类型无注解示例：

       Long id;
       String name = "Echo";

  基本类型且无注解，将默认为等于操作。以上注解将产生如下语句：

      name = ?

  注意以上id字段并没有生产条件，默认情况下，字段值为null将忽略这个字段。  null值或是空字符串，字段都将被忽略。



##### 10.1.2 复杂类型字段无注解

   复杂类型的定义为：

   a)非基本类型

   b)非基本类型的数组

   复杂类型无注解法例：

      //无注解
      DTO queryDTO = new DTO();

   以上字段将会被递归解析，所产生的语句将会被加入当前语句中。
   

#### 10.2 强制忽略

   可以通过Ignore注解强制忽略指定的字段或是类

     //忽略字段
     @Ignore
     String tempValue = "Echo";

     //忽略的类，整个类将被忽略
     @Ignore
     public class xxDTO{
     ...
     }

#### 10.3 有条件忽略(SPEL表达式)

   大部分的注解都有condition属性，以脚本的方式求值，目前只支持SpEL，当返回true时，表示注解有效，如：

      @Eq(condition="#_val != null")
      String name = "Echo";

#### 10.4 变量上下文

  SPEL 中可以使用，任意的查询语句中也都可以使用
   

  可用默认变量：

       #_val 表示字段的值
    
       #_this 表示DTO对象
    
       #_name 当前注解所在的字段名
    
       #_isSelect 表示当前是否是SelectDao
    
       #_isUpdate 表示当前是否是UpdateDao
    
       #_isDelete 表示当前是否是DeleteDao
   
   
   上下文列表（越后面优先级越高）：
   
      DaoContext.getGlobalContext()
      
      DaoContext.getThreadContext()
      
      dao 上下文
      
      参数上下文
      
            
#### 10.4 有效的注解

   如果注解标注在 Modifier.STATIC | Modifier.FINAL | Modifier.TRANSIENT 三种字段上将被忽略。


#### 10.6 自动值转换

   组件集成Spring的值转换功能，如果可以把字符串转换成数组，把字符串转换成日期、数值等。

   如下例子：
   
   JPA实体类字段定义：

      Long id;

      Date createTime;

   DTO类字段定义 ：

      String id;
 
      @GroupBy
      @DateTimeFormat(pattern = "yyyy-MM-dd")  
      String createTime;

   以上字段将被会自动转换成对应的类型。
   
   
   日期类型转换使用 Spring 的注解 DateTimeFormat
   
   数值类型转换使用 Spring 的注解 NumberFormat

#### 10.7 避免 N + 1 查询，关联属性的自动抓取(仅对JPA有效)

   1、一对多，多对一模型定义
  
        @Entity    
        class User{
                ...
               @ManyToOne(fetch = FetchType.LAZY) 
               @JoinColumn(name = "group_id")
               Group group;
        }     
        
        @Entity   
         class Group {
                  ...
                
                 @ManyToOne(fetch = FetchType.LAZY) 
                 protected T parent; 
                 
                 @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE) 
                 protected Set<T> children;
         }   
     
  2、通过代码抓取    
        
         //查询 User 实，直接通过连接获取所有的孩子节点，避免 N+1 查询   
         jpa.selectFrom(User.class,"u")
            .joinFetch("group.children") //设置立刻抓取 避免 N+1 查询 
            .find()   
        
  
  3、通过注解抓取
  
  查询对象和结果对象都可以增加抓取注解
    
        @Data
        @Accessors(chain = true)
        public class UserInfo {
      
            @Fetch //设置立刻抓取 避免 N+1 查询 
            Group group;
        
            @Fetch(value = "group.name") //设置立刻抓取 避免 N+1 查询 
            String groupName;
        
            @Fetch(value = "group.children" ) //设置立刻抓取 避免 N+1 查询 
            Collection<Group> parentChildren;
        
        }      
        
        //避免 N+1 查询
        List<UserInfo> userInfoList jpaDao.selectFrom(User.class, "u").find(UserInfo.class)     
        
### 11、安全模式

   数据安全是非常重要的事情，DAO 增加安全模式能避免一些因为疏忽导致的数据安全问题。

   在安全模式下，必须指定部分条件，不允许无条件的更新、删除、查询。
    
  默认情况下 Dao 都是安全模式，可以调用 disableSafeMode() 禁用安全模式，如下：
    
    jpaDao.deleteFrom(User.class)
                   .disableSafeMode()
                   .delete();
   
   
   
  安全控制接口定义
   
       public interface SafeController<T> {
       
           /**
            * 禁止安全模式
            */
           T disableSafeMode();
       
           /**
            * 安全模式
            * <p>
            * 在安全模式下，不允许无条件的查询、更新和删除
            *
            * @return
            */
           boolean isSafeMode();
           
       }
   
            
### 12、附录


#### 12.1 测试用例

 请参考测试用例： [com.levin.commons.dao.support.JpaDaoImplTest](./simple-dao-jpa-starter/src/test/java/com/levin/commons/dao/support/JpaDaoImplTest.java) 
     
     
#### 12.2 注解字段说明

    /**
     * 不是 NUll 对象 ，也不是空字符串
     */
    String NOT_NULL = "#_val != null and (!(#_val instanceof T(CharSequence)) ||  #_val.trim().length() > 0)";
 
    /**
     * 查询字段名称，默认为字段的属性名称
     * <p>
     * 对应数据库的字段名或是 Jpa 实体类的字段名
     *
     * @return
     */
    String value() default "";


    /**
     * 是否是having 操作
     * <p>
     * 只针对查询有效
     *
     * @return
     */
    boolean having() default false;


    /**
     * 是否用 NOT () 包围
     *
     * @return
     */
    boolean not() default false;


    /**
     * 是否是必须的，如果条件不匹配，但又是必须的，将抛出异常
     *
     * @return
     */
    boolean require() default false;


    /**
     * 表达式，默认为SPEL
     * <p>
     * <p>
     * 如果用 groovy:  做为前缀则是 groovy脚本
     * <p>
     *
     *
     * <p>
     * <p>
     * <p/>
     * 当条件成立时，整个条件才会被加入
     *
     * @return
     */
    String condition() default NOT_NULL;

    /**
     * 是否过滤数组参数或是列表参数中的空值
     * <p>
     * 主要针对 In NotIn Between
     *
     * @return
     */
    boolean filterNullValue() default true;


    /**
     * 针对字段函数列表
     * 后面的函数嵌套前面的函数
     * <p>
     * func3(func2(func1(t.field)
     *
     * <p>
     * <p>
     * 如果是更新字段则忽略
     *
     * @return
     */
    Func[] fieldFuncs() default {};


    /**
     * 针对参数的函数列表
     * <p>
     * 后面的函数嵌套前面的函数
     * <p>
     * 参数是指字段值或是子查询语句
     * <p>
     * 例如 func(:?)  把参数用函数包围
     * func(select name from user where id = :userId) 把子查询用函数包围
     *
     * @return
     */
    Func[] paramFuncs() default {};


    /**
     * 对整个表达式的包围前缀
     *
     * @return
     */
    String surroundPrefix() default "";

    /**
     * 子查询或是表达式
     *
     * @return
     */

    String paramExpr() default "";


    /**
     * 对整个表达式的包围后缀
     *
     * @return
     */
    String surroundSuffix() default "";


    /**
     * 描述信息
     *
     * @return
     */
    String desc() default "语句表达式生成规则： surroundPrefix + op.gen( func(fieldName), func([ paramExpr(优先) or 参数占位符 ])) +  surroundSuffix ";

  

#### 12.3 联系作者

 邮箱：99668980@qq.com

