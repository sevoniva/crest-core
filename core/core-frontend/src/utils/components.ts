import VText from '@/custom-component/v-text/Component.vue'
import VQuery from '@/custom-component/v-query/Component.vue'
import VTextAttr from '@/custom-component/v-text/Attr.vue'
import Group from '@/custom-component/group/Component.vue'
import GroupAttr from '@/custom-component/group/Attr.vue'
import UserView from '@/custom-component/user-view/Component.vue'
import UserViewAttr from '@/custom-component/user-view/Attr.vue'
import Picture from '@/custom-component/picture/Component.vue'
import PictureAttr from '@/custom-component/picture/Attr.vue'
import DynamicBackground from '@/custom-component/dynamic_background/Component.vue'
import DynamicBackgroundAttr from '@/custom-component/dynamic_background/Attr.vue'
import Decoration from '@/custom-component/decoration/Component.vue'
import DecorationAttr from '@/custom-component/decoration/Attr.vue'
import CanvasBoard from '@/custom-component/canvas-board/Component.vue'
import CanvasBoardAttr from '@/custom-component/canvas-board/Attr.vue'
import CanvasIcon from '@/custom-component/canvas-icon/Component.vue'
import CanvasIconAttr from '@/custom-component/canvas-icon/Attr.vue'
import Tabs from '@/custom-component/tabs/Component.vue'
import TabsAttr from '@/custom-component/tabs/Attr.vue'
import Graphical from '@/custom-component/graphical/Component.vue'
import GraphicalAttr from '@/custom-component/graphical/Attr.vue'
import CircleShape from '@/custom-component/circle-shape/Component.vue'
import CircleShapeAttr from '@/custom-component/circle-shape/Attr.vue'
import RectShape from '@/custom-component/rect-shape/Component.vue'
import RectShapeAttr from '@/custom-component/rect-shape/Attr.vue'
import SvgTriangle from '@/custom-component/svgs/svg-triangle/Component.vue'
import SvgTriangleAttr from '@/custom-component/svgs/svg-triangle/Attr.vue'
import TimeClock from '@/custom-component/time-clock/Component.vue'
import TimeClockAttr from '@/custom-component/time-clock/Attr.vue'
import GroupArea from '@/custom-component/group-area/Component.vue'
import GroupAreaAttr from '@/custom-component/group-area/Attr.vue'
import Frame from '@/custom-component/frame/ComponentFrame.vue'
import FrameAttr from '@/custom-component/frame/Attr.vue'
import Screen from '@/custom-component/screen/Component.vue'
import ScreenAttr from '@/custom-component/screen//Attr.vue'
import Video from '@/custom-component/video/Component.vue'
import VideoAttr from '@/custom-component/video/Attr.vue'
import StreamMedia from '@/custom-component/stream-media/Component.vue'
import StreamMediaAttr from '@/custom-component/stream-media/Attr.vue'
import ScrollText from '@/custom-component/scroll-text/Component.vue'
import ScrollTextAttr from '@/custom-component/scroll-text/Attr.vue'
import PopArea from '@/custom-component/pop-area/Component.vue'
import PopAreaAttr from '@/custom-component/pop-area/Attr.vue'
import PictureGroup from '@/custom-component/picture-group/Component.vue'
import PictureGroupAttr from '@/custom-component/picture-group/Attr.vue'
import ExportButton from '@/custom-component/export-button/Component.vue'
import ExportButtonAttr from '@/custom-component/export-button/Attr.vue'
export const componentsMap = {
  VText: VText,
  VQuery,
  VTextAttr: VTextAttr,
  Group: Group,
  GroupAttr: GroupAttr,
  UserView: UserView,
  UserViewAttr: UserViewAttr,
  Picture: Picture,
  PictureAttr: PictureAttr,
  DynamicBackground: DynamicBackground,
  DynamicBackgroundAttr: DynamicBackgroundAttr,
  CanvasBoard: CanvasBoard,
  CanvasBoardAttr: CanvasBoardAttr,
  CanvasIcon: CanvasIcon,
  CanvasIconAttr: CanvasIconAttr,
  Tabs: Tabs,
  TabsAttr: TabsAttr,
  Graphical: Graphical,
  GraphicalAttr: GraphicalAttr,
  CircleShape: CircleShape,
  CircleShapeAttr: CircleShapeAttr,
  RectShape: RectShape,
  RectShapeAttr: RectShapeAttr,
  SvgTriangle: SvgTriangle,
  SvgTriangleAttr: SvgTriangleAttr,
  TimeClock: TimeClock,
  TimeClockAttr: TimeClockAttr,
  GroupArea: GroupArea,
  GroupAreaAttr: GroupAreaAttr,
  Frame: Frame,
  FrameAttr: FrameAttr,
  Video: Video,
  VideoAttr: VideoAttr,
  StreamMedia: StreamMedia,
  StreamMediaAttr: StreamMediaAttr,
  ScrollText: ScrollText,
  ScrollTextAttr: ScrollTextAttr,
  PopArea: PopArea,
  PopAreaAttr: PopAreaAttr,
  PictureGroup: PictureGroup,
  PictureGroupAttr: PictureGroupAttr,
  ExportButton,
  ExportButtonAttr,
  Decoration: Decoration,
  DecorationAttr: DecorationAttr,
  Screen: Screen,
  ScreenAttr: ScreenAttr,
  DeDecoration: Decoration,
  DeDecorationAttr: DecorationAttr,
  DeFrame: Frame,
  DeFrameAttr: FrameAttr,
  DeGraphical: Graphical,
  DeGraphicalAttr: GraphicalAttr,
  DeScreen: Screen,
  DeScreenAttr: ScreenAttr,
  DeStreamMedia: StreamMedia,
  DeStreamMediaAttr: StreamMediaAttr,
  DeTabs: Tabs,
  DeTabsAttr: TabsAttr,
  DeTimeClock: TimeClock,
  DeTimeClockAttr: TimeClockAttr,
  DeVideo: Video,
  DeVideoAttr: VideoAttr
}

export default function findComponent(key) {
  return componentsMap[key]
}

// 整理输入数据并返回工具处理结果
export function findComponentAttr(component) {
  const key =
    component.component === 'UserView' && component.innerType === 'picture-group'
      ? 'PictureGroupAttr'
      : component.component + 'Attr'
  return componentsMap[key]
}
