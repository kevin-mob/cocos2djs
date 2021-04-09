var BaseListLayer = cc.Layer.extend({
    _spawnCount: 10,
    _totalCount: 0,
    _bufferZone: 50,
    _updateInterval: 0.1,
    _spacing: 0,
    _updateTimer: 0,
    _lastContentPosY: 0,
    _lastContentPosX: 0,
    _reuseItemOffset: 0,
    _initializeListSize: false,
    listView: null,
    defaultItem: null,
    direction: null,
    _array: [],
    _listViewLayoutInfo: [],
    _isReEnter: false,
    _listViewInnerContainerLastPosition:null,
    ctor: function () {
        this._super();

        // Create the list view
        this.listView = new ccui.ListView();
        this.listView.setTouchEnabled(true);
        this.listView.setBounceEnabled(true);
        this.listView.addEventListener(this.selectedItemEvent.bind(this));

        // set all items layout gravity
        this.listView.setGravity(ccui.ListView.GRAVITY_CENTER_VERTICAL);
        this.setupListView(this.listView);

        this.direction = this.listView.getLayoutType();
        this.addChild(this.listView);

        // create model
        this.defaultItem = new ccui.Layout();
        this.defaultItem.setTouchEnabled(true);

        this.setupItemModel(this.defaultItem);

        // set model
        this.listView.setItemModel(this.defaultItem);

        this.listView.setItemsMargin(this._spacing);
        if (this.direction == ccui.ScrollView.DIR_VERTICAL) {
            this._itemTemplateHeight = this.defaultItem.getContentSize().height;

            this._reuseItemOffset = (this._itemTemplateHeight + this._spacing) * this._spawnCount;
        } else if (this.direction == ccui.ScrollView.DIR_HORIZONTAL) {
            this._itemTemplateWidth = this.defaultItem.getContentSize().width;
            // FIXME 复用的偏移量为 原始_spawnCount 个view的宽度之和，可以改为根据ListView宽度自动计算复用宽度和_spawnCount，无需外部指定_spawnCount个数
            this._reuseItemOffset = (this._itemTemplateWidth + this._spacing) * this._spawnCount;
        }
    },

    /**
     *
     * @param listView {ccui.ListView}
     */
    setupListView: function (listView) {
        throw new Error("use BaseListLayer need override setupListView")
    },

    /**
     *  listView 默认的item模板
     * @param defaultItem {ccui.Layout}
     */
    setupItemModel: function (defaultItem) {
        throw new Error("use BaseListLayer need override setupItemModel")
    },

    /**
     * 进行itemLayout和数据绑定操作
     * @param itemLayout {ccui.Layout}
     * @param dataArray
     * @param index
     */
    onSetupItemData: function (itemLayout, dataArray, index) {
        throw new Error("use BaseListLayer need override onSetupItemData method")
    },

    setOnItemClickCallback: function (onItemClickCallback) {
        this.onItemClickCallback = onItemClickCallback;
    },

    setData: function (array) {
        this._isReEnter = false;
        this.listView.removeAllChildren();
        this._lastContentPosY = 0;
        this._lastContentPosX = 0;
        this._totalCount = 0;
        this.unscheduleUpdate();
        this._array = array;
        // 填充原始view
        for (let i = 0; i < array.length; i++) {
            // 超过_spawnCount数量的数据后停止预渲染
            if (i < this._spawnCount) {
                let item = new ccui.Layout();
                this.setupItemModel(item);
                item.setTag(i);
                this.onSetupItemData(item, array, i);
                this.listView.pushBackCustomItem(item);
            } else {
                break;
            }
        }
        this._totalCount = this._array.length;

        if (this.direction == ccui.ScrollView.DIR_VERTICAL) {
            let totalHeight = this._itemTemplateHeight * this._totalCount +
                (this._totalCount - 1) * this._spacing +
                this.listView.getTopPadding() + this.listView.getBottomPadding();
            if (totalHeight > this.listView.getContentSize().height) {
                this.listView.forceDoLayout();
                this.listView.getInnerContainer().setContentSize(cc.size(this.listView.getInnerContainerSize().width, totalHeight));
                //更新数据 移动内容到最前面
                this.listView.jumpToTop();
            }
        } else if (this.direction == ccui.ScrollView.DIR_HORIZONTAL) {
            let totalWidth = this._itemTemplateWidth * this._totalCount +
                (this._totalCount - 1) * this._spacing +
                this.listView.getLeftPadding() + this.listView.getRightPadding();
            if (totalWidth > this.listView.getContentSize().width) {
                this.listView.forceDoLayout();
                this.listView.getInnerContainer().setContentSize(cc.size(totalWidth, this.listView.getInnerContainerSize().height));
                //更新数据 移动内容到最前面
                this.listView.jumpToTop();
            }
        }

        this.scheduleUpdate();
    },

    getItemPositionYInView: function (item) {
        var worldPos = item.getParent().convertToWorldSpaceAR(item.getPosition());
        var viewPos = this.listView.convertToNodeSpaceAR(worldPos);
        return viewPos.y;
    },
    getItemPositionXInView: function (item) {
        var worldPos = item.getParent().convertToWorldSpaceAR(item.getPosition());
        var viewPos = this.listView.convertToNodeSpaceAR(worldPos);
        return viewPos.x;
    },

    update: function (dt) {
        this._updateTimer += dt;
        if (this._updateTimer < this._updateInterval) {
            return;
        }

        if(this._isReEnter)
            return;

        if (this.direction == ccui.ScrollView.DIR_VERTICAL) {
            this.updateVerticalList();
        } else if (this.direction == ccui.ScrollView.DIR_HORIZONTAL) {
            this.updateHorizontalList();
        }
    },

    updateVerticalList: function () {
        if (this.listView.getInnerContainer().getPosition().y === this._lastContentPosY) {
            return;
        }
        this._updateTimer = 0;

        var totalHeight = this._itemTemplateHeight * this._totalCount + (this._totalCount - 1) * this._spacing;
        var listViewHeight = this.listView.getContentSize().height;
        var items = this.listView.getItems();

        let itemCount = items.length;

        //手势的滑动方向
        var isDown = this.listView.getInnerContainer().getPosition().y < this._lastContentPosY;

        let itemID;
        for (var i = 0; i < itemCount && i < this._totalCount; ++i) {
            var item = items[i];
            var itemPos = this.getItemPositionYInView(item);
            if (isDown) {
                if (itemPos < -this._bufferZone - this.defaultItem.height && item.getPosition().y + this._reuseItemOffset < totalHeight) {
                    itemID = item.getTag() - itemCount;
                    cc.log("====== 下滑 itemID " + itemID);
                    item.setPositionY(item.getPositionY() + this._reuseItemOffset);
                    this.updateItem(itemID, i);
                }
            } else {
                if (itemPos > this._bufferZone + listViewHeight && item.getPositionY() - this._reuseItemOffset >= 0) {
                    item.setPositionY(item.getPositionY() - this._reuseItemOffset);
                    itemID = item.getTag() + itemCount;
                    cc.log("====== 上滑 itemID " + itemID);
                    this.updateItem(itemID, i);
                }
            }
        }
        this._lastContentPosY = this.listView.getInnerContainer().getPosition().y;
    },

    updateHorizontalList: function () {

        if (this.listView.getInnerContainer().getPosition().x === this._lastContentPosX) {
            return;
        }

        this._updateTimer = 0;

        var totalWidth = this._itemTemplateWidth * this._totalCount + (this._totalCount - 1) * this._spacing;
        var items = this.listView.getItems();

        // 屏幕在内容上的移动方向
        var isRight = this.listView.getInnerContainer().getPosition().x < this._lastContentPosX;
        // jumpToItem时，计算几倍重用
        var moveMultiple = Math.abs((this.listView.getInnerContainer().getPosition().x - this._lastContentPosX) / this._reuseItemOffset);
        moveMultiple = Math.ceil(moveMultiple);

        // 缓冲区设为4个模板view的宽度
        this._bufferZone = this._itemTemplateWidth * 4;

        if (isRight) {
            if (moveMultiple > 1) {
                // 跳跃式更新时（单次刷新x移动超过一屏），先刷新目标屏幕的前一屏数据，再刷新目标屏数据，保证显示没有空白
                this._ascendUpdate(moveMultiple - 1, totalWidth, items);
                this._ascendUpdate(1, totalWidth, items);
            } else {
                this._ascendUpdate(moveMultiple, totalWidth, items);
            }
        } else {
            if (moveMultiple > 1) {
                // 跳跃式更新时（单次刷新x移动超过一屏），先刷新目标屏幕的前一屏数据，再刷新目标屏数据，保证显示没有空白
                this._descendUpdate(moveMultiple - 1, totalWidth, items);
                this._descendUpdate(1, totalWidth, items);
            } else {
                this._descendUpdate(moveMultiple, totalWidth, items);
            }
        }
        this._lastContentPosX = this.listView.getInnerContainer().getPosition().x;
    },

    // 从左向右更新view，复用左边超出缓冲区的view
    _ascendUpdate: function (moveMultiple, totalWidth, items) {
        let dataIndex;
        let item;
        let itemPos;
        let itemCount = items.length;
        // 遍历items找到缓冲区左边的view进行复用， 计算的最终PositionX超过右边界停止更新，列表到头了
        for (let i = 0; i < itemCount && i < this._totalCount; i++) {
            item = items[i];
            itemPos = this.getItemPositionXInView(item);
            //找到缓冲区外面的view进行复用并且判断是否超出了总区域的右边界
            if (itemPos < -this._bufferZone && item.getPosition().x + this._reuseItemOffset * moveMultiple < totalWidth) {
                dataIndex = item.getTag() + itemCount * moveMultiple;
                item.setPositionX(item.getPositionX() + this._reuseItemOffset * moveMultiple);
                this.updateItem(dataIndex, i);
            }
        }
    },

    //从右向左更新view，复用右边超出缓冲区的view
    _descendUpdate: function (moveMultiple, totalWidth, items) {
        let dataIndex;
        let item;
        let itemPos;
        let itemCount = items.length;
        let listViewWidth = this.listView.getContentSize().width;
        // 遍历items找到缓冲区右边的view进行复用， 计算的最终PositionX超过左边界停止更新，列表到头了
        for (let i = Math.min(itemCount, this._totalCount) - 1; i >= 0; i--) {
            item = items[i];
            itemPos = this.getItemPositionXInView(item);
            //找到缓冲区右边的view进行复用，并且判断是否超出了总区域的左边界
            if (itemPos > this._bufferZone + listViewWidth && item.getPositionX() - this._reuseItemOffset * moveMultiple >= 0) {
                item.setPositionX(item.getPositionX() - this._reuseItemOffset * moveMultiple);
                dataIndex = item.getTag() - itemCount * moveMultiple;
                this.updateItem(dataIndex, i);
            }
        }
    },

    updateAllItem: function () {
        let items = this.listView.getItems();
        let item;
        let itemCount = items.length;
        for (let i = Math.min(itemCount, this._totalCount) - 1; i >= 0; i--) {
            item = items[i];
            this.onSetupItemData(item, this._array, item.getTag());
        }
    },

    updateItem: function (dataIndex, templateIndex) {
        var itemTemplate = this.listView.getItems()[templateIndex];
        itemTemplate.setTag(dataIndex);
        this.onSetupItemData(itemTemplate, this._array, dataIndex);
    },

    jumpToItem: function (index) {
        if (this.direction == ccui.ScrollView.DIR_VERTICAL) {
            let offset = index * (this._itemTemplateHeight + this._spacing);
            if (this.listView.getInnerContainer().height - offset < this.listView.height)
                offset = this.listView.getInnerContainer().height - this.listView.height;
            this.listView.getInnerContainer().setPositionY(-offset);
        } else if (this.direction == ccui.ScrollView.DIR_HORIZONTAL) {
            //index乘以单个item的偏移量获得index的绝对偏移量
            let offset = index * (this._itemTemplateWidth + this._spacing);
            // 剩余内容小于偏移值时，按剩余内容计算
            if (this.listView.getInnerContainer().width - offset < this.listView.width)
                offset = this.listView.getInnerContainer().width - this.listView.width;

            // positionX为0listview展示最左侧的内容， 相当于index=0， positionX为this.listView.getInnerContainer().width - this.listView.width时，展示到列表内容的最后面
            this.listView.getInnerContainer().setPositionX(-offset);
        }
    },

    /**
     * 类似于前后翻页的效果
     * @param isBackward 是否往回翻页
     */
    jumpToAdjacent: function (isBackward) {
        this.listView.stopAutoScroll();
        if (this.direction == ccui.ScrollView.DIR_VERTICAL) {
            let offset = this.listView.height + this._spacing;


            //上下超边界判断
            if(isBackward){ // getPositionY的值增加，不能超过0
                if (this.listView.getInnerContainer().getPositionY() + offset >= 0){
                    this.listView.getInnerContainer().setPositionY(0);
                }else {
                    this.listView.getInnerContainer().setPositionY(this.listView.getInnerContainer().getPositionY() + offset);
                }
            }else { // getPositionX的值减少，不能低于 -this.listView.getInnerContainer().width
                if (this.listView.getInnerContainer().getPositionY() - offset <= -this.listView.getInnerContainer().height + this.listView.height){
                    this.listView.getInnerContainer().getPositionY(-this.listView.getInnerContainer().height + this.listView.height);
                }else {
                    this.listView.getInnerContainer().getPositionY(this.listView.getInnerContainer().getPositionY() - offset);
                }
            }
        } else if (this.direction == ccui.ScrollView.DIR_HORIZONTAL) {
            let offset = this.listView.width + this._spacing;
            //左右超边界判断
            if(isBackward){ // getPositionX的值增加，不能超过0
                if (this.listView.getInnerContainer().getPositionX() + offset >= 0){
                    this.listView.getInnerContainer().setPositionX(0);
                }else {
                    this.listView.getInnerContainer().setPositionX(this.listView.getInnerContainer().getPositionX() + offset);
                }
            }else { // getPositionX的值减少，不能低于 -this.listView.getInnerContainer().width
                if (this.listView.getInnerContainer().getPositionX() - offset <= -this.listView.getInnerContainer().width + this.listView.width){
                    this.listView.getInnerContainer().setPositionX(-this.listView.getInnerContainer().width + this.listView.width);
                }else {
                    this.listView.getInnerContainer().setPositionX(this.listView.getInnerContainer().getPositionX() - offset);
                }
            }
        }
    },


    onExit: function () {
        this._super();
        // 解决listView onExit再次onEnter时，layout数据被自行修改的问题
        this.saveListViewLayoutInfo();
        this._isReEnter = true;
    },

    onEnter: function () {
        this._super();
        // 解决listView onExit再次onEnter时，layout数据被自行修改的问题
        if (this._isReEnter) {
            setTimeout(function () {
                this.restoreListViewLayoutInfo();
                this._isReEnter = false;
            }.bind(this), 300);
        }
    },

    saveListViewLayoutInfo: function () {
        this._listViewLayoutInfo = [];
        let items = this.listView.getItems();
        for (let i = 0; i < items.length; i++) {
            this._listViewLayoutInfo.push(this.direction === ccui.ScrollView.DIR_HORIZONTAL ? items[i].getPositionX() : items[i].getPositionY());
        }

        this._listViewInnerContainerLastPosition = this.direction === ccui.ScrollView.DIR_HORIZONTAL ?
            this.listView.getInnerContainer().getPositionX() : this.listView.getInnerContainer().getPositionY();
    },

    restoreListViewLayoutInfo: function () {

        if(!cc.sys.isObjectValid(this.listView) || this._listViewLayoutInfo.length === 0)
            return;

        let isHorizontal = this.direction === ccui.ScrollView.DIR_HORIZONTAL;
        if(isHorizontal){
            this.listView.getInnerContainer().setPositionX(this._listViewInnerContainerLastPosition);
        }else {
            this.listView.getInnerContainer().setPositionY(this._listViewInnerContainerLastPosition);
        }

        let items = this.listView.getItems();
        for (let i = 0; i < items.length; i++) {
            if (isHorizontal) {
                items[i].setPositionX(this._listViewLayoutInfo[i]);
            } else {
                items[i].setPositionY(this._listViewLayoutInfo[i]);
            }
        }
    },

    selectedItemEvent: function (sender, type) {
        switch (type) {
            case ccui.ListView.ON_SELECTED_ITEM_END:
                let item = sender.getItem(sender.getCurSelectedIndex());
                cc.log("select child index = " + item.getTag());
                if (this.onItemClickCallback) {
                    this.onItemClickCallback(this._array[item.getTag()], item.getTag());
                }
                break;
            default:
                break;
        }
    }
});