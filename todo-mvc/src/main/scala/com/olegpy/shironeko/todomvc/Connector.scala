package com.olegpy.shironeko.todomvc

import com.olegpy.shironeko.DirectConnector
import monix.eval.Task


object Connector extends DirectConnector[Task, TodoStore]
