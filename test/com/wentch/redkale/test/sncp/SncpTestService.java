/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.sncp;

import com.wentch.redkale.service.*;

/**
 *
 * @author zhangjx
 */
public class SncpTestService implements Service {

    public String queryResult(SncpTestBean bean) {
        System.out.println("运行了方法");
        return "result: " + bean;
    }
}
