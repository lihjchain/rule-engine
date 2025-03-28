/**
 * Copyright (c) 2020 dingqianwen (761945125@qq.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ruleengine.core.decisiontable.strategey;

import cn.ruleengine.core.decisiontable.CollHeadCompare;
import cn.ruleengine.core.decisiontable.Row;
import cn.ruleengine.core.value.Value;

import java.util.List;
import java.util.Map;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2020/12/19
 * @since 1.0.0
 */
public class HighestPrioritySingleStrategy implements Strategy {

    private static HighestPrioritySingleStrategy highestPrioritySingleStrategy = new HighestPrioritySingleStrategy();

    public static HighestPrioritySingleStrategy getInstance() {
        return highestPrioritySingleStrategy;
    }

    /**
     * 先从高优先级规则执行，返回命中的最高优先级所有结果
     *
     * @param collHeadCompareMap 表头比较器
     * @param decisionTree       决策树
     * @return 命中的结果值
     */
    @Override
    public List<Value> compute(Map<Integer, CollHeadCompare> collHeadCompareMap, Map<Integer, List<Row>> decisionTree) {
        // TODO: 2020/12/19
        return null;
    }

}
