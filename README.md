解决app使用runtimeOnly依赖组件后，组件的DataBinderMapperImpl不能被app依赖。
此插件修改class文件，将各组件的DataBinderMapperImpl插入到app的DataBinderMapperImpl的collectDependencies的list中。

注意：此项目的app主工程，不用来测试插件。如果需要测试插件，请自行修改app和插件的代码。


插入前

```
  @Override
  public List<DataBinderMapper> collectDependencies() {
    ArrayList<DataBinderMapper> result = new ArrayList<DataBinderMapper>(2);
    result.add(new androidx.databinding.library.baseAdapters.DataBinderMapperImpl());
    return result;
  }
```


插入后

```
  @Override
  public List<DataBinderMapper> collectDependencies() {
    ArrayList<DataBinderMapper> result = new ArrayList<DataBinderMapper>(2);
    result.add(new androidx.databinding.library.baseAdapters.DataBinderMapperImpl());
    result.add(new com.wawj.demo.DataBinderMapperImpl());
    return result;
  }
```



